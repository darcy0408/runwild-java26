package dev.runwild.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * User's location and personal tolerances.
 *
 * <p>Every threshold here is deliberately a <em>preference</em>, not a constant. A runner
 * with hay fever and a runner who has never sneezed in their life should get different
 * answers from the same forecast; that personalisation is the point of the tool.
 *
 * <p>Loaded from {@code runwild.properties} in the working directory when present,
 * otherwise from the bundled defaults.
 */
public record Config(
        String locationName,
        double latitude,
        double longitude,
        double idealTempMinF,
        double idealTempMaxF,
        /** 0.0 = pollen is irrelevant to me, 1.0 = typical, 2.0 = severe hay fever. */
        double pollenSensitivity,
        /** How long the run is, in whole hours; drives the best-window search. */
        int runDurationHours,
        /** How far ahead to plan. Open-Meteo serves up to 7 days. */
        int forecastDays,
        /** Refuse to recommend running in the dark. */
        boolean avoidDarkness) {

    public Config {
        if (latitude < -90 || latitude > 90)
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        if (longitude < -180 || longitude > 180)
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        if (idealTempMinF >= idealTempMaxF)
            throw new IllegalArgumentException(
                    "idealTempMinF (%.1f) must be below idealTempMaxF (%.1f)"
                            .formatted(idealTempMinF, idealTempMaxF));
        if (pollenSensitivity < 0 || pollenSensitivity > 2)
            throw new IllegalArgumentException("pollenSensitivity must be 0..2");
        if (runDurationHours < 1) throw new IllegalArgumentException("runDurationHours must be >= 1");
        if (forecastDays < 1 || forecastDays > 7)
            throw new IllegalArgumentException("forecastDays must be 1..7");
    }

    /** Placeholder location — override in runwild.properties. */
    public static Config defaults() {
        return new Config("Denver, CO", 39.7392, -104.9903,
                40.0, 65.0, 1.0, 1, 2, true);
    }

    public static Config load() {
        Path file = Path.of("runwild.properties");
        if (!Files.exists(file)) return defaults();
        var props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("Could not read runwild.properties, using defaults: " + e.getMessage());
            return defaults();
        }
        return from(props);
    }

    public static Config from(Properties props) {
        Config d = defaults();
        return new Config(
                props.getProperty("location.name", d.locationName()),
                num(props, "location.latitude", d.latitude()),
                num(props, "location.longitude", d.longitude()),
                num(props, "ideal.temp.min.f", d.idealTempMinF()),
                num(props, "ideal.temp.max.f", d.idealTempMaxF()),
                num(props, "pollen.sensitivity", d.pollenSensitivity()),
                (int) num(props, "run.duration.hours", d.runDurationHours()),
                (int) num(props, "forecast.days", d.forecastDays()),
                Boolean.parseBoolean(props.getProperty("avoid.darkness",
                        String.valueOf(d.avoidDarkness()))));
    }

    private static double num(Properties props, String key, double fallback) {
        String raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            System.err.printf("Bad value for %s ('%s'), using %s%n", key, raw, fallback);
            return fallback;
        }
    }

    /** Weather: temperature, comfort, wind, rain, UV, and today's sunrise/sunset. */
    public String forecastUrl() {
        return """
               https://api.open-meteo.com/v1/forecast\
               ?latitude=%.4f&longitude=%.4f\
               &hourly=temperature_2m,apparent_temperature,relative_humidity_2m,\
               precipitation_probability,wind_speed_10m,uv_index\
               &daily=sunrise,sunset\
               &temperature_unit=fahrenheit&wind_speed_unit=kmh\
               &timezone=auto&forecast_days=%d"""
                .formatted(latitude, longitude, forecastDays);
    }

    /** Air quality: US AQI, particulates, and the pollens that matter to runners. */
    public String airQualityUrl() {
        return """
               https://air-quality-api.open-meteo.com/v1/air-quality\
               ?latitude=%.4f&longitude=%.4f\
               &hourly=us_aqi,pm2_5,ozone,ragweed_pollen,grass_pollen,birch_pollen\
               &timezone=auto&forecast_days=%d"""
                .formatted(latitude, longitude, forecastDays);
    }

    /** Active NWS watches, warnings and advisories for this point (US only). */
    public String alertsUrl() {
        return "https://api.weather.gov/alerts/active?point=%.4f,%.4f"
                .formatted(latitude, longitude);
    }
}
