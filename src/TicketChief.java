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
            server.start(8000);
        } catch (IOException e) {
            System.err.println("I/O Exception: " + e.getMessage());
        }
    }
}
