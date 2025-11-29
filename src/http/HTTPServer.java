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
        System.out.printf(
            ANSI.PURPLE_BOLD_BRIGHT + "HTTP server listening on %s:%d%n" + ANSI.RESET,
            server.getInetAddress().getHostAddress(),
            server.getLocalPort()
        );
    }

    protected void onConnect(Socket socket) {
        System.out.printf(
            ANSI.CYAN_BOLD_BRIGHT + "*** New connection: %s:%d%n" + ANSI.RESET,
            socket.getInetAddress().getHostName(),
            socket.getPort()
        );
    }

    private static String buildLogSuffix(Map<String, String> headers, String body) {
        StringBuilder suffix = new StringBuilder();

        int headerCount = headers.size();
        if (headerCount > 0) {
            suffix.append(" (").append(headerCount).append(" header");
            if (headerCount > 1) {
                suffix.append("s");
            }
            suffix.append(")");
        }

        int bodySize = body.getBytes().length;
        if (bodySize > 0) {
            suffix.append(" [").append(body.length()).append(" byte");
            if (bodySize > 1) {
                suffix.append("s");
            }
            suffix.append("]");
        }

        return suffix.toString();
    }

    protected void onRequest(Request request) {
        String message = (
            ANSI.YELLOW + "--> "
            + request.method() + " "
            + request.path()
            + buildLogSuffix(request.headers(), request.body())
            + ANSI.RESET
        );

        System.out.println(message);
    }

    protected void onResponse(Response response) {
        String colour = response.statusCode() >= 400 ? ANSI.RED : ANSI.GREEN;
        String message = (
            colour + "<-- "
            + response.statusCode() + " "
            + response.getStatusMessage()
            + buildLogSuffix(response.headers(), response.body())
            + ANSI.RESET
        );

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

    private Map<String, String> parseHeaders(BufferedReader in) throws BadRequestException, IOException {
        // https://www.rfc-editor.org/rfc/rfc9112.html#name-field-syntax
        Map<String, String> headers = new HashMap<>();
        String line;
        while (!(line = in.readLine().strip()).isEmpty()) {
            String[] parts = line.split(":\\s*", 2);
            if (parts.length < 2) {
                throw new BadRequestException("Invalid header line: " + line);
            }
            headers.put(parts[0], parts[1]);
        }
        return headers;
    }

    private String parseBody(BufferedReader in, Map<String, String> headers) throws BadRequestException, IOException {
        // this is a cursed abomination of an implementation but good enough for this coursework

        String contentLengthString =  headers.get("Content-Length");
        if (contentLengthString == null) {
            // assume no body content
            return null;
        }

        int contentLength;
        try {
            contentLength = Integer.parseInt(contentLengthString);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid Content-Length: " + contentLengthString);
        }

        char[] buffer = new char[contentLength];
        int cursor = 0;
        while (cursor < contentLength) {
            int read = in.read(buffer);
            if (read == -1) {
                throw new IOException("Unexpected end of stream");
            }
            cursor += read;
        }

        return new String(buffer);
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

    private void handleClient(Socket socket, BufferedReader in, BufferedWriter out) throws BadRequestException, IOException {
        onConnect(socket);
        Response response;

        try {
            // parse http request line
            // https://www.rfc-editor.org/rfc/rfc9112.html#name-request-line
            Matcher requestLine = REQUEST_LINE_PATTERN.matcher(in.readLine());
            if (!requestLine.matches()) {
                throw new BadRequestException("Invalid request line: " + requestLine);
            }
            String method = requestLine.group("method");
            String path = requestLine.group("path");

            // parse headers and body from request
            Map<String, String> headers = parseHeaders(in);
            String body = parseBody(in, headers);

            // construct Request object
            Request request = new Request(method, path, headers, body);
            onRequest(request);
            response = routeRequest(request);
        } catch (BadRequestException e) {
            // construct bad request response instead
            response = Response.HttpCatResponse(400);
        }

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
