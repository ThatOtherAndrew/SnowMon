package http;

import java.util.*;

public final class Request {
    private final String method;
    private final String path;
    private final Map<String, String> headers;
    private final Map<String, String> routeParams = new HashMap<>();

    public Request(String method, String path, Map<String, String> headers) {
        this.method = method;
        this.path = path;
        this.headers = headers;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String getRouteParam(String key) {
        return routeParams.get(key);
    }

    public void setRouteParam(String key, String value) {
        routeParams.put(key, value);
    }
}
