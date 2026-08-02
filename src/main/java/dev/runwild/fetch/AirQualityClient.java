package dev.runwild.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parses the Open-Meteo air-quality response: US AQI, particulates and pollen. */
public final class AirQualityClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record AirHour(
            LocalDateTime time, Integer usAqi, Double pm25, Double ozone,
            Double ragweed, Double grass, Double birch) {}

    /** Keyed by hour so the merge with the weather series is a lookup, not a zip. */
    public Map<LocalDateTime, AirHour> parse(String json) throws Exception {
        JsonNode hourly = MAPPER.readTree(json).path("hourly");
        JsonNode times = hourly.path("time");

        var byHour = new LinkedHashMap<LocalDateTime, AirHour>();
        for (int i = 0; i < times.size(); i++) {
            LocalDateTime time = Json.localDateTime(Json.text(times, i));
            if (time == null) continue;
            byHour.put(time, new AirHour(
                    time,
                    Json.integer(hourly.path("us_aqi"), i),
                    Json.dbl(hourly.path("pm2_5"), i),
                    Json.dbl(hourly.path("ozone"), i),
                    Json.dbl(hourly.path("ragweed_pollen"), i),
                    Json.dbl(hourly.path("grass_pollen"), i),
                    Json.dbl(hourly.path("birch_pollen"), i)));
        }
        return byHour;
    }
}
