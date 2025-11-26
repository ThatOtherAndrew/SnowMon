import http.HTTPServer;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        HTTPServer server = new TicketChiefServer();
        try {
            server.start(8000);
        } catch (IOException e) {
            System.err.println("I/O Exception: " + e.getMessage());
        }
    }
}
