package dev.runwild.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Parses the Open-Meteo forecast response: comfort, wind, rain, UV, and daylight. */
public final class WeatherClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Per-hour weather, plus the sunrise/sunset pairs needed to decide daylight. */
    public record WeatherHour(
            LocalDateTime time, Double tempF, Double apparentF, Double humidity,
            Double windKph, Double precipChance, Double uvIndex) {}

    public record Daylight(Map<LocalDate, LocalDateTime> sunrise,
                           Map<LocalDate, LocalDateTime> sunset) {

        /** Hours outside sunrise..sunset are dark. Days we have no data for count as light. */
        public boolean isDaylight(LocalDateTime hour) {
            LocalDate date = hour.toLocalDate();
            LocalDateTime up = sunrise.get(date);
            LocalDateTime down = sunset.get(date);
            if (up == null || down == null) return true;
            return !hour.isBefore(up) && !hour.isAfter(down);
        }
    }

    public record Forecast(List<WeatherHour> hours, Daylight daylight) {}

    public Forecast parse(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        JsonNode hourly = root.path("hourly");
        JsonNode times = hourly.path("time");

        var hours = new ArrayList<WeatherHour>();
        for (int i = 0; i < times.size(); i++) {
            LocalDateTime time = Json.localDateTime(Json.text(times, i));
            if (time == null) continue;
            hours.add(new WeatherHour(
                    time,
                    Json.dbl(hourly.path("temperature_2m"), i),
                    Json.dbl(hourly.path("apparent_temperature"), i),
                    Json.dbl(hourly.path("relative_humidity_2m"), i),
                    Json.dbl(hourly.path("wind_speed_10m"), i),
                    Json.dbl(hourly.path("precipitation_probability"), i),
                    Json.dbl(hourly.path("uv_index"), i)));
        }

        JsonNode daily = root.path("daily");
        var sunrise = new HashMap<LocalDate, LocalDateTime>();
        var sunset = new HashMap<LocalDate, LocalDateTime>();
        JsonNode days = daily.path("time");
        for (int i = 0; i < days.size(); i++) {
            LocalDate date = LocalDate.parse(Json.text(days, i));
            LocalDateTime up = Json.localDateTime(Json.text(daily.path("sunrise"), i));
            LocalDateTime down = Json.localDateTime(Json.text(daily.path("sunset"), i));
            if (up != null) sunrise.put(date, up);
            if (down != null) sunset.put(date, down);
        }

        return new Forecast(List.copyOf(hours), new Daylight(sunrise, sunset));
    }
}
