package http;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.function.Function;

public abstract class HTTPServer {
    private final Map<Route, Function<Route, Response>> routes = new IdentityHashMap<>();

    public void addRoute(Route route, Function<Route, Response> handler) {
        this.routes.put(route, handler);
    }

    private void onConnect(Socket socket) {
        System.out.println("New connection: " + socket.getInetAddress());
    }

    public void start(int port) throws IOException {
        try (ServerSocket server = new ServerSocket(port);) {
            while (true) {
                try (
                    Socket socket = server.accept();
                    // gosh we sure love java don't we
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))
                ) {
                    this.onConnect(socket);
                }
            }
        }
    }
}