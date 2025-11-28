import events.Event;
import http.HTTPServer;
import http.Response;
import utils.PropertiesReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class TicketChief {
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
        registerRoutes(server, event);

        try {
            server.start(port);
        } catch (Exception e) {
            System.err.printf("Fatal server error: %s: %s%n", e.getClass().getName(), e.getMessage());
        }
    }

    private static void registerRoutes(HTTPServer server, Event event) {
        server.route("GET", "/tickets", request -> {
            if (!"application/json".equals(request.headers().get("Accept"))) {
                return Response.HttpCatResponse(406);
            }

            return new Response(200, Map.of("Content-Type", "application/json"), event.toJSON());
        });
    }
}
