package http;

import utils.ANSI;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HTTPServer {
    private static final Pattern REQUEST_LINE_PATTERN = Pattern.compile(
        "^"
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
        System.out.printf(ANSI.PURPLE_BOLD_BRIGHT + "HTTP server listening on %s:%d%n", server.getInetAddress().getHostAddress(), server.getLocalPort());
    }

    protected void onConnect(Socket socket) {
        System.out.println(ANSI.CYAN_BOLD_BRIGHT + "*** New connection: " + socket.getInetAddress().getHostName() + ":" + socket.getPort());
    }

    protected void onRequest(Request request) {
        StringBuilder message = new StringBuilder(ANSI.YELLOW).append("--> ")
            .append(request.method()).append(" ")
            .append(request.path());

        int headerCount = request.headers().size();
        if (headerCount > 0) {
            message.append(" (").append(headerCount).append(" header");
            if (headerCount > 1) {
                message.append("s");
            }
            message.append(")");
        }

        System.out.println(message);
    }

    protected void onResponse(Response response) {
        String colour = response.statusCode() >= 400 ? ANSI.RED : ANSI.GREEN;
        StringBuilder message = new StringBuilder(colour).append("<-- ")
            .append(response.statusCode()).append(" ")
            .append(response.getStatusMessage());

        int bodySize = response.getBodySize();
        if (bodySize > 0) {
            message.append(" (").append(response.body().length()).append(" byte");
            if (bodySize > 1) {
                message.append("s");
            }
            message.append(")");
        }

        System.out.println(message);
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
                String contentType = Files.probeContentType(filePath); // guess content type
                return new Response(200, Map.of("Content-Type", contentType), fileContent);
            } catch (IOException e) {
                System.err.printf("Failed to read requested file: %s: %s%n", filePath, e.getMessage());
                return Response.HttpCatResponse(404);
            }
        }

        // fallback 404 response
        return Response.HttpCatResponse(404);
    }

    protected Response errorRoute(Exception e) {
        System.err.printf("Server error when handling request: %s: %s%n", e.getClass().getName(), e.getMessage());
        return Response.HttpCatResponse(500);
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

    private String parseBody(BufferedReader in) throws IOException {
        // this is a cursed abomination which violates line endings and probably corrupts non-text data
        // probably good enough for the assignment though
        StringBuilder body = new StringBuilder();
        String line;

        while ((line = in.readLine()) != null) {
            body.append(line).append("\n");
        }

        return body.toString();
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
        Matcher requestLine = REQUEST_LINE_PATTERN.matcher(in.readLine());
        if (!requestLine.matches()) {
            // TODO: handle bad request
            return;
        }
        String method = requestLine.group("method");
        String path = requestLine.group("path");

        Request request = new Request(method, path, parseHeaders(in), parseBody(in));
        onRequest(request);
        Response response = routeRequest(request);
        onResponse(response);
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
