package dev.runwild.fetch;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpOption;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP/3-first client for RunWild's upstream data sources.
 *
 * <p><b>Why this class exists rather than a bare {@link HttpClient}:</b> setting
 * {@code .version(HTTP_3)} on the client is <em>not</em> sufficient to actually get
 * HTTP/3. By default the JDK uses {@link Http3DiscoveryMode#ALT_SVC} discovery, which
 * requires the server to have advertised h3 in an {@code Alt-Svc} header on a previous
 * response. The first request therefore silently goes out over HTTP/2 or HTTP/1.1.
 * Requesting {@link Http3DiscoveryMode#ANY} lets the client attempt h3 immediately.
 *
 * <p>Not every host or network supports QUIC — corporate proxies frequently block it
 * outright — so every request falls back cleanly, and the protocol that was
 * <em>actually</em> negotiated is reported back to the caller and surfaced in the UI.
 * Claiming HTTP/3 is easy; showing the negotiated protocol per source is honest.
 */
public final class Http implements AutoCloseable {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    /**
     * api.weather.gov rejects requests without a User-Agent with HTTP 403, and asks that
     * it identify the application. This is a documented requirement of that API, not a
     * workaround.
     */
    private static final String USER_AGENT =
            "RunWild/1.0 (https://github.com/; hackster.io contest project)";

    private final HttpClient http3 = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_3)
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final HttpClient fallback = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** A response body plus the protocol version genuinely negotiated for it. */
    public record Result(String body, HttpClient.Version protocol, long millis) {

        /** "HTTP/3", "HTTP/2", "HTTP/1.1" — for display. */
        public String protocolLabel() {
            return switch (protocol) {
                case HTTP_3 -> "HTTP/3";
                case HTTP_2 -> "HTTP/2";
                case HTTP_1_1 -> "HTTP/1.1";
            };
        }
    }

    /**
     * GET {@code url}, attempting HTTP/3 first and degrading gracefully.
     *
     * @throws java.io.IOException if both the h3 and the fallback attempt fail
     */
    public Result get(String url) throws Exception {
        long start = System.nanoTime();

        try {
            HttpResponse<String> response = http3.send(
                    request(url).setOption(HttpOption.H3_DISCOVERY, Http3DiscoveryMode.ANY).build(),
                    HttpResponse.BodyHandlers.ofString());

            if (isSuccess(response)) {
                return new Result(response.body(), response.version(), elapsedMs(start));
            }
            // A non-2xx is a server-side answer, not a transport problem: do not retry it
            // over HTTP/2 and double the load. Report it.
            throw new java.io.IOException(
                    "HTTP " + response.statusCode() + " from " + host(url));

        } catch (java.io.IOException | InterruptedException transportFailure) {
            if (transportFailure instanceof java.io.IOException io
                    && io.getMessage() != null
                    && io.getMessage().startsWith("HTTP ")) {
                throw io;   // genuine server error, already meaningful
            }
            // QUIC blocked, UDP filtered, or the host simply speaks no h3.
            HttpResponse<String> response = fallback.send(
                    request(url).build(), HttpResponse.BodyHandlers.ofString());

            if (!isSuccess(response)) {
                throw new java.io.IOException(
                        "HTTP " + response.statusCode() + " from " + host(url));
            }
            return new Result(response.body(), response.version(), elapsedMs(start));
        }
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .GET();
    }

    private static boolean isSuccess(HttpResponse<?> response) {
        return response.statusCode() / 100 == 2;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String host(String url) {
        try {
            return URI.create(url).getHost();
        } catch (RuntimeException e) {
            return url;
        }
    }

    @Override
    public void close() {
        http3.close();
        fallback.close();
    }

    // ------------------------------------------------------------------
    // Demo mode
    // ------------------------------------------------------------------

    /**
     * Proves HTTP/3 is real rather than merely requested, by forcing
     * {@link Http3DiscoveryMode#HTTP_3_URI_ONLY} — which fails outright rather than
     * silently downgrading — against a host known to speak h3.
     */
    public static void demo() {
        String target = "https://cloudflare-quic.com/";
        System.out.println("Forcing HTTP/3 (HTTP_3_URI_ONLY, no fallback permitted)");
        System.out.println("  target: " + target);

        try (HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_3)
                .connectTimeout(CONNECT_TIMEOUT)
                .build()) {

            HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                    .header("User-Agent", USER_AGENT)
                    .setOption(HttpOption.H3_DISCOVERY, Http3DiscoveryMode.HTTP_3_URI_ONLY)
                    .timeout(REQUEST_TIMEOUT)
                    .build();

            long start = System.nanoTime();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.printf("  status: %d%n", response.statusCode());
            System.out.printf("  negotiated protocol: %s%n", response.version());
            System.out.printf("  elapsed: %d ms%n", elapsedMs(start));
            System.out.println(response.version() == HttpClient.Version.HTTP_3
                    ? "  => HTTP/3 confirmed over QUIC."
                    : "  => NOT HTTP/3 (unexpected for this host).");

        } catch (Exception e) {
            System.out.println("  => HTTP/3 unavailable on this network: " + e);
            System.out.println("     (UDP/443 is commonly blocked by proxies and some VPNs.)");
        }
    }
}
