package dev.runwild.fetch;

import dev.runwild.config.Config;
import dev.runwild.model.Alert;
import dev.runwild.model.Hour;
import dev.runwild.model.Telemetry;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.StructuredTaskScope;

/**
 * Fetches every upstream source concurrently and merges them into one hourly series.
 *
 * <p>Uses {@link StructuredTaskScope} (JEP 505, a preview API in Java 26 — the build
 * passes {@code --enable-preview}). The three sources are independent, so running them
 * sequentially would make the user wait for the sum of three round trips instead of the
 * slowest one. Structured concurrency also guarantees that no subtask outlives the scope:
 * if the weather fetch fails, the air-quality fetch is cancelled rather than leaking.
 *
 * <p>Weather and air quality are required. NWS alerts are deliberately <em>optional</em>:
 * that endpoint is the flakiest of the three and a missing severe-weather feed should
 * degrade the advice, not destroy it. The subtask therefore swallows its own failure and
 * the UI states plainly that alerts were unavailable.
 */
public final class DataFetcher {

    private final WeatherClient weatherClient = new WeatherClient();
    private final AirQualityClient airQualityClient = new AirQualityClient();
    private final AlertsClient alertsClient = new AlertsClient();

    public record Bundle(List<Hour> hours, List<Alert> alerts, Telemetry telemetry) {}

    public Bundle fetch(Config config) throws Exception {
        long start = System.nanoTime();

        try (var http = new Http();
             var scope = StructuredTaskScope.open()) {

            var weather = scope.fork(() -> http.get(config.forecastUrl()));
            var airQuality = scope.fork(() -> http.get(config.airQualityUrl()));
            var alerts = scope.fork(() -> optional(() -> http.get(config.alertsUrl())));

            scope.join();

            Http.Result weatherResult = weather.get();
            Http.Result airResult = airQuality.get();
            Http.Result alertsResult = alerts.get();

            var forecast = weatherClient.parse(weatherResult.body());
            var airByHour = airQualityClient.parse(airResult.body());
            List<Alert> activeAlerts = alertsResult == null
                    ? List.of()
                    : alertsClient.parse(alertsResult.body());

            long totalMs = (System.nanoTime() - start) / 1_000_000;

            var telemetry = new Telemetry(
                    totalMs,
                    weatherResult.protocolLabel(),
                    airResult.protocolLabel(),
                    alertsResult == null ? "unavailable" : alertsResult.protocolLabel(),
                    alertsResult != null);

            return new Bundle(merge(forecast, airByHour), activeAlerts, telemetry);
        }
    }

    /**
     * The same three fetches, one after another. Exists purely so the README can quote a
     * measured speed-up rather than asserting one.
     */
    public long timeSequential(Config config) throws Exception {
        long start = System.nanoTime();
        try (var http = new Http()) {
            http.get(config.forecastUrl());
            http.get(config.airQualityUrl());
            optional(() -> http.get(config.alertsUrl()));
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    /** Merge the two hourly series and resolve daylight for each hour. */
    private List<Hour> merge(WeatherClient.Forecast forecast,
                             Map<LocalDateTime, AirQualityClient.AirHour> airByHour) {
        var merged = new ArrayList<Hour>(forecast.hours().size());
        for (WeatherClient.WeatherHour w : forecast.hours()) {
            AirQualityClient.AirHour air = airByHour.get(w.time());
            merged.add(new Hour(
                    w.time(),
                    w.tempF(), w.apparentF(), w.humidity(), w.windKph(),
                    w.precipChance(), w.uvIndex(),
                    air == null ? null : air.usAqi(),
                    air == null ? null : air.pm25(),
                    air == null ? null : air.ozone(),
                    air == null ? null : air.ragweed(),
                    air == null ? null : air.grass(),
                    air == null ? null : air.birch(),
                    forecast.daylight().isDaylight(w.time())));
        }
        return List.copyOf(merged);
    }

    /** Runs a fetch whose failure is tolerable, yielding null instead of propagating. */
    private static Http.Result optional(ThrowingSupplier supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            System.err.println("Optional source unavailable: " + e.getMessage());
            return null;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Http.Result get() throws Exception;
    }
}
