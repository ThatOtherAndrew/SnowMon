import http.HTTPServer;
import http.Response;

import java.io.IOException;
import java.util.Map;

public class TicketChief {
    public static void main(String[] args) {
        HTTPServer server = new HTTPServer();

        // test out some routes...
        server.route("GET", "/", request ->
            new Response(200, Map.of(), "woah")
        );
        server.route("GET", "/:slug", request ->
            new Response(200, Map.of(),"you went to " + request.getRouteParam("slug"))
        );

        try {
            PropertiesReader properties = new PropertiesReader("cs2003-C3.properties");
            server.start(properties.getIntProperty("serverPort", 8000));
        } catch (Exception e) {
            System.err.printf("Fatal exception: %s: %s%n", e.getClass().getName(), e.getMessage());
        }
    }
}
