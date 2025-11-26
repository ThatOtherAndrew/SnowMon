package http;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Route {
    // Match e.g. ":bar" from "/foo/:bar", with just "bar" in $1
    private static final Pattern ROUTE_PARAMS_PATTERN = Pattern.compile("(?<=/):([^/]+)");
    // Then turn it into a regex pattern to match it into a named capture group
    private static final String ROUTE_PARAM_SUBSTITUTION = "(?<$1>[^/]+)";

    private final String method;
    private final Pattern pathPattern;
    private final List<String> routeParams;

    public Route(String method, String path) {
        this.method = method;

        String escapedPath = Pattern.quote(path);
        Matcher paramMatcher = ROUTE_PARAMS_PATTERN.matcher(escapedPath);

        List<String> params = new ArrayList<>();
        while (paramMatcher.find()) {
            params.add(paramMatcher.group(1));
        }
        this.routeParams = params;

        String regex = paramMatcher.reset().replaceAll(ROUTE_PARAM_SUBSTITUTION);
        this.pathPattern = Pattern.compile(regex);
    }

    public boolean matches(Request request) {
        if (!request.method().equals(this.method)) return false;

        Matcher matcher = pathPattern.matcher(request.path());
        if (!matcher.matches()) return false;

        // at this point the request should match the route
        for (String param : routeParams) {
            request.setRouteParam(param, matcher.group(param));
        }
        return true;
    }
}
