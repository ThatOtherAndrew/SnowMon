package http;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("ClassCanBeRecord")
public class Route {
    private final String method;
    private final String[] pathSegments;

    public Route(String method, String path) {
        this.method = method;
        this.pathSegments = path.split("/");
    }

    public boolean matches(Request request) {
        // compare methods
        if (!request.method().equals(method)) return false;

        // compare paths
        String[] requestPath = request.path().split("/");
        if (requestPath.length != pathSegments.length) return false;

        Map<String, String> routeParams = new HashMap<>();
        for (int i = 0; i < pathSegments.length; i++) {
            if (pathSegments[i].startsWith(":")) {  // route param segment?
                routeParams.put(pathSegments[i].substring(1), requestPath[i]);
            } else if (!pathSegments[i].equals(requestPath[i])) {  // segment doesn't match!
                return false;
            }
        }

        // at this point the request should match the route
        for (Map.Entry<String, String> routeParam : routeParams.entrySet()) {
            request.setRouteParam(routeParam.getKey(), routeParam.getValue());
        }
        return true;
    }
}
