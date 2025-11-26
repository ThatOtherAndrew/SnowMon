package http;

import java.util.Map;

public record Response(int statusCode, Map<String, String> headers, String body) {
    private static final Map<Integer, String> STATUS_CODE_MESSAGES = Map.ofEntries(
        // Some common HTTP status codes
        Map.entry(200, "OK"),
        Map.entry(201, "Created"),
        Map.entry(400, "Bad Request"),
        Map.entry(404, "Not Found"),
        Map.entry(500, "Internal Server Error")
        // We haven't covered all of them but that's no bother!
        // According to the RFC 9112, reason-phrase is optional anyways :D
        // so a fallback of an empty string will do
    );

    public String render() {
        // https://www.rfc-editor.org/rfc/rfc9112.html#name-status-line
        // http 1.1 is the only real http version, everything else is a conspiracy theory
        // it's now 7:24am and my sleep deprived brain is losing it can you tell

        StringBuilder response = new StringBuilder("HTTP/1.1 ")
            .append(statusCode).append(" ")
            .append(STATUS_CODE_MESSAGES.getOrDefault(statusCode, ""))
            .append("\r\n");  // HTTP spec wants CRLF specifically

        for (Map.Entry<String, String> header : headers.entrySet()) {
            response.append(header.getKey()).append(": ")
                .append(header.getValue()).append("\r\n");
        }

        response.append("\r\n").append(body);

        return response.toString();
    }
}
