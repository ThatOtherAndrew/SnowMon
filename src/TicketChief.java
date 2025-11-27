import http.HTTPServer;
import http.Response;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class TicketChief {
    public static void main(String[] args) {
        try {
            // read config with fallback values
            PropertiesReader properties = new PropertiesReader("cs2003-C3.properties");
            Path documentRoot = Paths.get(properties.getStringProperty("documentRoot", "public"));
            int port = properties.getIntProperty("serverPort", 8000);

            HTTPServer server = new HTTPServer(documentRoot);
            registerRoutes(server);
            server.start(port);
        } catch (Exception e) {
            System.err.printf("Fatal exception: %s: %s%n", e.getClass().getName(), e.getMessage());
        }
    }

    private static void registerRoutes(HTTPServer server) {
        server.route("GET", "/tickets", request ->
            new Response(200, Map.of(),"you went to " + request.getRouteParam("slug"))
        );
    }
}
