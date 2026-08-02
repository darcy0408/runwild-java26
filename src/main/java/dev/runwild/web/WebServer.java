package dev.runwild.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.runwild.PlanService;
import dev.runwild.RunContext;
import dev.runwild.config.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Serves the dashboard and its JSON API from the JDK's own HTTP server.
 *
 * <p>No web framework: {@code com.sun.net.httpserver} plus virtual threads covers
 * everything this needs, which keeps the dependency list at one library and the whole
 * thing reproducible with {@code mvn package}.
 *
 * <p>Each request runs on its own virtual thread, with the caller's tolerances bound
 * into a {@link RunContext} {@link ScopedValue} for its dynamic extent. That is what
 * lets two people hit the same server and get answers tuned to their own hay fever,
 * without a settings parameter threaded through every method — and unlike a
 * {@code ThreadLocal}, there is nothing to leak or clean up afterwards.
 */
public final class WebServer {

    private final int port;

    public WebServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/api/plan", this::handlePlan);
        server.createContext("/", this::handleStatic);
        server.start();

        System.out.printf("""

                RunWild dashboard: http://localhost:%d
                  API:  http://localhost:%d/api/plan
                  Stop: Ctrl+C
                %n""", port, port);
    }

    // ------------------------------------------------------------------

    private void handlePlan(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed");
            return;
        }
        try {
            Config config = configFor(exchange.getRequestURI());

            // Bind this caller's tolerances for the duration of the request.
            String json = ScopedValue.where(RunContext.CONFIG, config)
                    .call(() -> ApiJson.write(new PlanService().build()));

            exchange.getResponseHeaders().add("Cache-Control", "no-store");
            send(exchange, 200, "application/json; charset=utf-8", json);

        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            send(exchange, 502, "application/json; charset=utf-8",
                    "{\"error\":\"Could not reach the weather services\",\"detail\":%s}"
                            .formatted(quote(message)));
        }
    }

    /** Query parameters override the file-based config, one request at a time. */
    private Config configFor(URI uri) {
        Config base = Config.load();
        Map<String, String> params = queryParams(uri);
        if (params.isEmpty()) return base;

        try {
            return new Config(
                    params.getOrDefault("name", base.locationName()),
                    dbl(params, "lat", base.latitude()),
                    dbl(params, "lon", base.longitude()),
                    dbl(params, "tempMin", base.idealTempMinF()),
                    dbl(params, "tempMax", base.idealTempMaxF()),
                    dbl(params, "pollen", base.pollenSensitivity()),
                    (int) dbl(params, "duration", base.runDurationHours()),
                    (int) dbl(params, "days", base.forecastDays()),
                    !"false".equals(params.getOrDefault("dark",
                            String.valueOf(base.avoidDarkness()))));
        } catch (IllegalArgumentException badInput) {
            // A nonsensical override (min above max, latitude of 900) falls back to the
            // configured values rather than 500-ing the dashboard.
            return base;
        }
    }

    private static double dbl(Map<String, String> params, String key, double fallback) {
        String raw = params.get(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Map<String, String> queryParams(URI uri) {
        var params = new HashMap<String, String>();
        String query = uri.getQuery();
        if (query == null || query.isBlank()) return params;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                params.put(java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    // ------------------------------------------------------------------

    private void handleStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        // Only ever serve the three bundled assets. No path arithmetic, no traversal.
        String resource = switch (path) {
            case "/index.html" -> "/web/index.html";
            case "/style.css" -> "/web/style.css";
            case "/app.js" -> "/web/app.js";
            default -> null;
        };
        if (resource == null) {
            send(exchange, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }

        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                send(exchange, 500, "text/plain; charset=utf-8", "Missing bundled asset: " + resource);
                return;
            }
            byte[] body = in.readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", contentType(resource));
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    private static String contentType(String resource) {
        if (resource.endsWith(".html")) return "text/html; charset=utf-8";
        if (resource.endsWith(".css")) return "text/css; charset=utf-8";
        if (resource.endsWith(".js")) return "text/javascript; charset=utf-8";
        return "application/octet-stream";
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ") + "\"";
    }
}
