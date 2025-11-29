import events.Event;
import events.PurchaseManager;
import http.HTTPServer;
import http.Response;
import utils.PropertiesReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TicketChief {
    private static final Pattern PURCHASE_JSON_PATTERN = Pattern.compile(
        "\\s*\\{"
        + "\\s*\"tickets\"\\s*:\\s*(?<tickets>\\d+)\\s*"
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
        Path eventsPath =  Paths.get(properties.getStringProperty("eventsPath", "tickets.json"));

        // read tickets.json file (or whatever else the file is named)
        Event event;
        try {
            String[] eventJsonStrings = Files.readString(eventsPath)
                .replaceAll("^\\s*\\[|]\\s*$", "") // remove outermost array brackets
                .split("(?<=})\\s*,"); // split into separate objects
            event = Event.fromJSON(eventJsonStrings[0]); // get first event
        } catch (IOException e) {
            System.err.println("Failed to read tickets.json file: " + e.getMessage());
            return;
        }

        // init server
        HTTPServer server = new HTTPServer(documentRoot);
        registerRoutes(server, new PurchaseManager(event));

        try {
            server.start(port);
        } catch (Exception e) {
            System.err.printf("Fatal server error: %s: %s%n", e.getClass().getName(), e.getMessage());
        }
    }

    private static void registerRoutes(HTTPServer server, PurchaseManager purchaseManager) {
        // GET /tickets
        server.route("GET", "/tickets", request -> {
            if (!"application/json".equals(request.headers().get("Accept"))) {
                return Response.HttpCatResponse(406); // Not Acceptable
            }

            return new Response(
                200,
                Map.of("Content-Type", "application/json"),
                purchaseManager.getEvent().toJSON()
            );
        });

        // POST /queue
        server.route("POST", "/queue", request -> {
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
            int ticketCount = Integer.parseInt(matcher.group("tickets"));

            // return 200 if not enough tickets available
            if (ticketCount > purchaseManager.getEvent().getTicketCount()) {
                return Response.HttpCatResponse(200); // OK (not created)
            }

            int requestId = purchaseManager.requestPurchase(ticketCount).id();
            return new Response(
                201,
                Map.of("Content-Type", "application/json", "Location", "/queue/" + requestId),
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

        // GET /queue/:id
        server.route("GET", "/queue/:id", request -> {
            if (!"application/json".equals(request.headers().get("Accept"))) {
                return Response.HttpCatResponse(406); // Not Acceptable
            }

            int id;
            try {
                id = Integer.parseInt(request.getRouteParam("id"));
            } catch (NumberFormatException e) {
                return Response.HttpCatResponse(404); // Tread non-numeric IDs as unknown / not found
            }

            String requestStatus = purchaseManager.getRequestStatusJson(id);
            if (requestStatus == null) {
                return Response.HttpCatResponse(404); // Not Found
            }

            return new Response(200, Map.of("Content-Type", "application/json"), requestStatus);
        });
    }
}
