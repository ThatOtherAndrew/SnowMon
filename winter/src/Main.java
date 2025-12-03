import events.Event;
import events.Events;
import events.InvalidEventException;
import events.PurchaseManager;
import http.HTTPServer;
import http.Response;
import utils.NonceManager;
import utils.PropertiesReader;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final Pattern PURCHASE_JSON_PATTERN = Pattern.compile(
        "\\s*\\{"
        + "\\s*\"eventId\"\\s*:\\s*(?<eventId>\\d+)\\s*,\\s*"
        + "\\s*\"tickets\"\\s*:\\s*(?<tickets>\\d+)\\s*"
        + "\\s*}\\s*"
    );

    private static final Pattern REFUND_JSON_PATTERN = Pattern.compile(
        "\\s*\\{\\s*"
        + "\"ticketIds\"\\s*:\\s*\\[\\s*(?<ticketIds>"
        + "(?:\"[^\"]*\"(?:\\s*,\\s*\"[^\"]*\")*)?"
        + ")\\s*]\\s*"
        + "\\s*}\\s*"
    );

    public static void main(String[] args) {
        // read config with fallback values
        PropertiesReader properties;
        try {
            properties = new PropertiesReader("cs2003-C3.properties");
        } catch (IOException e) {
            System.err.println("Failed to read cs2003-C3.properties file: " + e.getMessage());
            return;
        }

        int port = properties.getIntProperty("serverPort", 8000);
        Path documentRoot = Paths.get(properties.getStringProperty("documentRoot", "public"));
        Path eventsPath = Paths.get(properties.getStringProperty("eventsPath", "tickets.json"));

        Events events;
        try {
            events = Events.fromJSONFile(eventsPath);
        } catch (IOException e) {
            System.err.println("Failed to read tickets.json file: " + e.getMessage());
            return;
        }

        // init server
        HTTPServer server = new HTTPServer(documentRoot);
        registerSnowMonRoutes(server);
        registerTicketChiefRoutes(server, new PurchaseManager(events));

        try {
            server.start(port);
        } catch (Exception e) {
            System.err.printf("Fatal server error: %s: %s%n", e.getClass().getName(), e.getMessage());
        }
    }

    public static void registerSnowMonRoutes(HTTPServer server) {
        // GET /snowmon
        server.route("GET", "/snowmon", request -> new Response(
            200,
            Map.of("Content-Type", "application/json"),
            String.format(
                """
                {
                    "memoryUsage": %f
                }
                """.trim(),
                1. - ((double) Runtime.getRuntime().freeMemory() / Runtime.getRuntime().totalMemory())
            )
        ));
    }

    private static void registerTicketChiefRoutes(HTTPServer server, PurchaseManager purchaseManager) {
        // Nonce manager for replay attack prevention (Part 3 security)
        NonceManager nonceManager = new NonceManager();

        // GET /ticketchief/tickets
        server.route("GET", "/ticketchief/tickets", request -> new Response(
            200,
            Map.of("Content-Type", "application/json"),
            purchaseManager.getEventsAsJson()
        ));

        // GET /ticketchief/tickets/:id
        server.route("GET", "/ticketchief/tickets/:id", request -> {
            if (!"application/json".equals(request.headers().get("Accept"))) {
                return Response.HttpCatResponse(406); // Not Acceptable
            }

            try {
                return new Response(
                    200,
                    Map.of("Content-Type", "application/json"),
                    purchaseManager.getEvent(request.getRouteParam("id")).toJSON()
                );
            } catch (InvalidEventException e) {
                return Response.HttpCatResponse(404); // Not Found
            }
        });

        // POST /ticketchief/tickets/:id/refund
        server.route("POST", "/ticketchief/tickets/:id/refund", request -> {
            // Validate nonce for replay attack prevention
            if (!nonceManager.validateNonce(request.headers().get("X-Nonce"))) {
                return Response.HttpCatResponse(400); // Bad Request (missing or reused nonce)
            }

            if (!"application/json".equals(request.headers().get("Content-Type"))) {
                return Response.HttpCatResponse(415); // Unsupported Media Type
            }

            Matcher matcher = REFUND_JSON_PATTERN.matcher(request.body());
            if (!matcher.find()) { // invalid JSON
                return Response.HttpCatResponse(400); // Bad Request
            }
            String arrayContents = matcher.group("ticketIds");
            // this is sooo janky but good enough for the purposes of the "json parsing" of this assignment
            List<String> ticketIds = Arrays.stream(arrayContents.split("\\s*,\\s*"))
                .map(s -> s.replaceAll("\"", "")) // remove quotes around strings
                .toList();

            Event event;
            try {
                event = purchaseManager.getEvent(request.getRouteParam("id"));
            } catch (InvalidEventException e) {
                return Response.HttpCatResponse(404); // Not Found
            }

            if (!event.refundTickets(ticketIds)) {
                // in the current implementation of refundTickets this will never happen
                // but hey, I'm Forward-Thinking™ !!!
                return Response.HttpCatResponse(422); // Unprocessable Entity
            }

            return Response.HttpCatResponse(204); // No Content
        });

        // POST /ticketchief/queue
        server.route("POST", "/ticketchief/queue", request -> {
            // Validate nonce for replay attack prevention
            if (!nonceManager.validateNonce(request.headers().get("X-Nonce"))) {
                return Response.HttpCatResponse(400); // Bad Request (missing or reused nonce)
            }

            if (!"application/json".equals(request.headers().get("Accept"))) {
                return Response.HttpCatResponse(406); // Not Acceptable
            }

            if (!"application/json".equals(request.headers().get("Content-Type"))) {
                return Response.HttpCatResponse(415); // Unsupported Media Type
            }

            Matcher matcher = PURCHASE_JSON_PATTERN.matcher(request.body());
            if (!matcher.find()) { // invalid JSON
                return Response.HttpCatResponse(400); // Bad Request
            }
            int eventId = Integer.parseInt(matcher.group("eventId"));
            int ticketCount = Integer.parseInt(matcher.group("tickets"));

            // return 200 if not enough tickets available
            if (ticketCount > purchaseManager.getEvent(eventId).getTicketCount()) {
                return Response.HttpCatResponse(200); // OK (not created)
            }

            int requestId;
            try {
                requestId = purchaseManager.requestPurchase(eventId, ticketCount).id();
            } catch (InvalidEventException e) {
                return Response.HttpCatResponse(422); // Unprocessable Entity
            }

            return new Response(
                201,
                Map.of("Content-Type", "application/json", "Location", "/ticketchief/queue/" + requestId),
                String.format(
                    """
                    {
                        "id": %d
                    }
                    """.trim(),
                    requestId
                )
            );
        });

        // GET /ticketchief/queue/:id
        server.route("GET", "/ticketchief/queue/:id", request -> {
            if (!"application/json".equals(request.headers().get("Accept"))) {
                return Response.HttpCatResponse(406); // Not Acceptable
            }

            int id;
            try {
                id = Integer.parseInt(request.getRouteParam("id"));
            } catch (NumberFormatException e) {
                return Response.HttpCatResponse(404); // Treat non-numeric IDs as unknown / not found
            }

            String requestStatus = purchaseManager.getRequestStatusJson(id);
            if (requestStatus == null) {
                return Response.HttpCatResponse(404); // Not Found
            }

            return new Response(200, Map.of("Content-Type", "application/json"), requestStatus);
        });

        // DELETE /ticketchief/queue/:id
        server.route("DELETE", "/ticketchief/queue/:id", request -> {
            // Validate nonce for replay attack prevention
            if (!nonceManager.validateNonce(request.headers().get("X-Nonce"))) {
                return Response.HttpCatResponse(400); // Bad Request (missing or reused nonce)
            }

            int id;
            try {
                id = Integer.parseInt(request.getRouteParam("id"));
            } catch (NumberFormatException e) {
                return Response.HttpCatResponse(404); // Treat non-numeric IDs as unknown / not found
            }

            try {
                boolean cancelled = purchaseManager.cancelPurchaseRequest(id);
                if (!cancelled) { // Can't cancel, request already fulfilled!
                    return Response.HttpCatResponse(409); // Conflict
                }
            } catch (IllegalArgumentException e) {
                return Response.HttpCatResponse(404); // Not Found
            }

            return new Response(204, Map.of(), ""); // No Content
        });
    }
}
