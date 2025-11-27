package http;

import com.jogamp.common.util.ArrayHashMap;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HTTPServer {
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
        + "(?<path>(?:/[^/]*)+)"
        + "\\s+"

        // https://www.rfc-editor.org/rfc/rfc9112.html#name-http-version
        // let's be real we are not handling anything outside of HTTP/1.1
        + "HTTP/1.1$"
    );

    private final Map<Route, Function<Request, Response>> routes = new LinkedHashMap<>();
    private final Path documentRoot;

    public HTTPServer(Path documentRoot) {
        if (!Files.isDirectory(documentRoot)) {
            throw new IllegalArgumentException(String.format("Document root directory '%s' not found", documentRoot));
        }
        this.documentRoot = documentRoot;
    }

    public void route(String method, String path, Function<Request, Response> handler) {
        this.routes.put(new Route(method, path), handler);
    }

    protected void onReady(ServerSocket server) {
        System.out.printf("HTTP server listening on %s:%d%n", server.getInetAddress().getHostAddress(), server.getLocalPort());
    }

    protected void onConnect(Socket socket) {
        System.out.println("New connection: " + socket.getInetAddress().getHostName() + ":" + socket.getPort());
    }

    protected void onRequest(Request request) {
        System.out.printf("%s %s %s%n", request.method(), request.path(), request.headers());
    }

    protected Response defaultRoute(Request request) {
        // redirect root requests to index.html
        String path = request.path();
        if (request.path().equals("/")) {
            path = "/index.html";
        }

        // try to serve from document root
        Path filePath = Paths.get(documentRoot.toString(), path);
        if (Files.isRegularFile(filePath) && Files.isReadable(filePath)) {
            try {
                String fileContent = Files.readString(filePath);
                return new Response(200, Map.of(), fileContent);
            } catch (IOException e) {
                String message = String.format("Failed to read requested file: %s: %s", filePath, e.getMessage());
                System.err.println(message);
                return new Response(500, Map.of(), message);
            }
        }

        // fallback 404 response
        return new Response(404, Map.of(), String.format("wah 404\n%s %s not found", request.method(), request.path()));
    }

    protected Response errorRoute(Exception e) {
        // TODO: more sensible response
        System.err.printf("Server error when handling request: %s: %s%n", e.getClass().getName(), e.getMessage());
        return new Response(500, Map.of(), "wah 500\n" + e.getMessage());
    }

    private Map<String, String> parseHeaders(BufferedReader in) throws IOException {
        // https://www.rfc-editor.org/rfc/rfc9112.html#name-field-syntax
        Map<String, String> headers = new HashMap<>();
        String line;
        while (!(line = in.readLine().strip()).isEmpty()) {
            String[] parts = line.split(":\\s*");
            headers.put(parts[0], parts[1]);
        }
        return headers;
    }

    private Response routeRequest(Request request) {
        for (Route route : routes.keySet()) {
            if (route.matches(request)) {
                Response response = routes.get(route).apply(request);
                if (response != null) {
                    return response;
                }
            }
        }

        // oops, no matches
        return defaultRoute(request);
    }

    private void handleClient(Socket socket, BufferedReader in, BufferedWriter out) throws IOException {
        onConnect(socket);

        // https://www.rfc-editor.org/rfc/rfc9112.html#name-request-line
        Matcher requestLine = REQUEST_LINE.matcher(in.readLine());
        if (!requestLine.matches()) {
            // TODO: handle bad request
            return;
        }
        String method = requestLine.group("method");
        String path = requestLine.group("path");

        Request request = new Request(method, path, parseHeaders(in));
        onRequest(request);
        Response response = routeRequest(request);
        out.write(response.render());
    }

    public void start(int port) throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            onReady(server);

            while (!server.isClosed()) {
                try (
                    Socket socket = server.accept();
                    // gosh we sure love java don't we
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))
                ) {
                    try {
                        handleClient(socket, in, out);
                    } catch (Exception e) {
                        out.write(errorRoute(e).render());
                    }
                }
            }
        }
    }
}
