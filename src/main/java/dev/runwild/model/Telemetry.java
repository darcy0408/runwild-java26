package dev.runwild.model;

/**
 * How the fetch behaved, surfaced in the UI rather than hidden in logs.
 *
 * <p>Showing the protocol each source actually negotiated keeps the HTTP/3 claim honest:
 * QUIC is blocked on plenty of networks, and a dashboard that silently says "HTTP/3"
 * while running over HTTP/1.1 would be lying.
 */
public record Telemetry(
        long totalMillis,
        String weatherProtocol,
        String airQualityProtocol,
        String alertsProtocol,
        boolean alertsAvailable) {

    /** "HTTP/3 · HTTP/3 · HTTP/2" */
    public String protocolSummary() {
        return "%s · %s · %s".formatted(weatherProtocol, airQualityProtocol, alertsProtocol);
    }
}
