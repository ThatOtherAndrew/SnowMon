package http;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class HTTPServer {
    private static final Pattern REQUEST_LINE = Pattern.compile("^"
        // https://www.rfc-editor.org/rfc/rfc9112.html#name-method
        // technically according to RFC 9110 section 5.6.2, tokens can have all sorts of goofy characters in them
        // if i was doing this "properly", i'd have to match those
        // but handling methods such as "PO$T" is stupid so i'll just use `\w+`
        + "(?<method>\\w+)"
        + "\\s+"

        // https://www.rfc-editor.org/rfc/rfc9112.html#name-request-target
        // i'm only gonna handle origin-form because the other forms are silly (for this assignment)
        // again, taking a simplistic view of what uri segments look like because RFC 3986 3.3 is dumb
        // also, screw query params for now
        + "(?<path>(?:/\\S+)+)"
        + "\\s+"

        // https://www.rfc-editor.org/rfc/rfc9112.html#name-http-version
        // let's be real we are not handling anything outside of HTTP/1.1
        + "HTTP/1.1$"
    );

    private final Map<Route, Function<Request, Response>> routes = new IdentityHashMap<>();

    public void addRoute(Route route, Function<Request, Response> handler) {
        this.routes.put(route, handler);
    }

    protected void onConnect(Socket socket) {
        System.out.println("New connection: " + socket.getInetAddress());
    }

    protected void onRequest(Request request) {
        System.out.printf("%s %s (%s)", request.method(), request.path(), request.headers());
    }

    private Map<String, String> parseHeaders(BufferedReader in) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        while (!(line = in.readLine().strip()).isEmpty()) {
            String[] parts = line.split(":\\s*");
            headers.put(parts[0], parts[1]);
        }
        return headers;
    }

    private void routeRequest(Request request) {
        for (Route route : routes.keySet()) {
        }
    }

    public void start(int port) throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            while (!server.isClosed()) {
                try (
                    Socket socket = server.accept();
                    // gosh we sure love java don't we
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))
                ) {
                    onConnect(socket);
                    
                    // https://www.rfc-editor.org/rfc/rfc9112.html#name-request-line
                    Matcher requestLine = REQUEST_LINE.matcher(in.readLine());
                    String method = requestLine.group("method");
                    String path = requestLine.group("path");

                    Request request = new Request(method, path, parseHeaders(in));
                    onRequest(request);
                    routeRequest(request);
                }
            }
        }
    }
}
