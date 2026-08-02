package dev.runwild.fetch;

import dev.runwild.model.Alert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parses real, committed API responses.
 *
 * <p>The fixtures are genuine captures rather than hand-written samples, so these tests
 * catch upstream shape changes — and because they are committed, the suite runs with no
 * network at all.
 */
class ParsingTest {

    private static String fixture(String name) throws IOException {
        try (InputStream in = ParsingTest.class.getResourceAsStream("/" + name)) {
            assertNotNull(in, "missing test fixture: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("parses 48 hours of weather with sunrise and sunset")
    void parsesForecast() throws Exception {
        var forecast = new WeatherClient().parse(fixture("forecast-denver.json"));

        assertEquals(48, forecast.hours().size());

        var first = forecast.hours().getFirst();
        assertNotNull(first.time());
        assertNotNull(first.tempF());
        assertNotNull(first.apparentF());
        assertNotNull(first.uvIndex());

        // Denver in August: the middle of the night is dark, the middle of the day is not.
        LocalDateTime midnight = first.time().toLocalDate().atTime(2, 0);
        LocalDateTime noon = first.time().toLocalDate().atTime(13, 0);
        assertFalse(forecast.daylight().isDaylight(midnight), "2am must not be daylight");
        assertTrue(forecast.daylight().isDaylight(noon), "1pm must be daylight");
    }

    @Test
    @DisplayName("parses air quality, and keeps absent pollen absent rather than zero")
    void parsesAirQualityWithMissingPollen() throws Exception {
        Map<LocalDateTime, AirQualityClient.AirHour> hours =
                new AirQualityClient().parse(fixture("airquality-denver.json"));

        assertEquals(48, hours.size());

        var sample = hours.values().iterator().next();
        assertNotNull(sample.usAqi(), "US AQI is available in the US");
        assertNotNull(sample.ozone(), "ozone is available in the US");

        // The whole reason the scorer distinguishes null from zero: Open-Meteo's pollen
        // model covers Europe only, so every US pollen value comes back null.
        assertTrue(hours.values().stream().allMatch(h -> h.grass() == null),
                "US pollen is expected to be absent, not zero");
    }

    @Test
    @DisplayName("parses European pollen, which does have coverage")
    void parsesEuropeanPollen() throws Exception {
        Map<LocalDateTime, AirQualityClient.AirHour> hours =
                new AirQualityClient().parse(fixture("airquality-berlin.json"));

        assertTrue(hours.values().stream().anyMatch(h -> h.grass() != null),
                "Berlin should have grass pollen readings");
    }

    @Test
    @DisplayName("parses NWS alerts including severity and validity window")
    void parsesAlerts() throws Exception {
        List<Alert> alerts = new AlertsClient().parse(fixture("alerts-denver.json"));

        assertFalse(alerts.isEmpty(), "the captured fixture contains active alerts");
        assertTrue(alerts.stream().anyMatch(a -> a.event().equals("Heat Advisory")));

        for (Alert alert : alerts) {
            assertNotNull(alert.event());
            assertNotNull(alert.severity());
            assertNotNull(alert.headline());
        }
    }

    @Test
    @DisplayName("survives empty and malformed payloads without throwing")
    void toleratesEmptyPayloads() throws Exception {
        assertTrue(new AlertsClient().parse("{\"features\":[]}").isEmpty());
        assertTrue(new AlertsClient().parse("{}").isEmpty());
        assertTrue(new AirQualityClient().parse("{}").isEmpty());
        assertTrue(new WeatherClient().parse("{}").hours().isEmpty());
    }
}
