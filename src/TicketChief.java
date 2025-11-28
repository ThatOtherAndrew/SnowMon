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
            // get first event
            event = Event.fromJSON(Files.readString(eventsPath).split(",")[0]);
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
        server.route("GET", "/events", request ->
            new Response(200, Map.of(),"you went to " + request.getRouteParam("slug"))
        );
    }
}
