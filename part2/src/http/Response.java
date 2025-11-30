package http;

import java.util.HashMap;
import java.util.Map;

public record Response(int statusCode, Map<String, String> headers, String body) {
    private static final Map<Integer, String> STATUS_CODE_MESSAGES = Map.ofEntries(
        // Some common HTTP status codes
        Map.entry(200, "OK"),
        Map.entry(201, "Created"),
        Map.entry(204, "No Content"),
        Map.entry(400, "Bad Request"),
        Map.entry(404, "Not Found"),
        Map.entry(406, "Not Acceptable"),
        Map.entry(409, "Conflict"),
        Map.entry(415, "Unsupported Media Type"),
        Map.entry(500, "Internal Server Error")
        // We haven't covered all of them but that's no bother!
        // According to the RFC 9112, reason-phrase is optional anyways :D
        // so a fallback of a dummy string will do
    );

    public static Response HttpCatResponse(int statusCode) {
        // language=AngularHTML
        String html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>{code} {message}</title>
                <style>
                    body {
                        background-color: black;
                        height: 100vh;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                    }

                    img {
                        max-width: 100%;
                        max-height: 100%;
                    }
                </style>
            </head>
            <body>
                <img src="https://http.cat/{code}" alt="{code} {message}">
            </body>
            </html>
            """
            .replaceAll("\\{code\\}", String.valueOf(statusCode))
            .replaceAll("\\{message\\}", STATUS_CODE_MESSAGES.getOrDefault(statusCode, "Response"));

        return new Response(statusCode, Map.of("Content-Type", "text/html"), html);
    }

    public String getStatusMessage() {
        return STATUS_CODE_MESSAGES.getOrDefault(statusCode, "Unknown");
    }

    public String render() {
        // add Content-Length header based on body
        Map<String, String> patchedHeaders = new HashMap<>(headers);
        patchedHeaders.put("Content-Length", String.valueOf(body.getBytes().length));

        // https://www.rfc-editor.org/rfc/rfc9112.html#name-status-line
        // http 1.1 is the only real http version, everything else is a conspiracy theory
        // it's now 7:24am and my sleep deprived brain is losing it can you tell
        StringBuilder response = new StringBuilder("HTTP/1.1 ")
            .append(statusCode).append(" ").append(getStatusMessage())
            .append("\r\n");  // HTTP spec wants CRLF specifically

        for (Map.Entry<String, String> header : patchedHeaders.entrySet()) {
            response.append(header.getKey()).append(": ")
                .append(header.getValue()).append("\r\n");
        }

        response.append("\r\n").append(body);

        return response.toString();
    }
}
