package dev.runwild.score;

import dev.runwild.config.Config;
import dev.runwild.model.Advisory;
import dev.runwild.model.Alert;
import dev.runwild.model.Hour;
import dev.runwild.model.Score;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns one hour of conditions into a 0-100 runnability score.
 *
 * <p>Starts at 100 and subtracts itemised penalties, so every point lost has a named
 * cause the user can see. The thresholds come from {@link Config} rather than being
 * hard-coded: the same forecast should score differently for a runner with severe hay
 * fever than for one without, and that is the whole point of the tool.
 *
 * <p>Missing data is never scored as good data. When a source has no value for a factor,
 * no penalty is applied but an {@link Advisory.Unknown} is attached, so an optimistic
 * score is always accompanied by the reason it might be wrong.
 */
public final class Scorer {

    private static final double MAX_TEMP_PENALTY = 40;
    private static final double MAX_WIND_PENALTY = 25;
    private static final double SEVERE_ALERT_SCORE_CAP = 20;

    private final Config config;

    public Scorer(Config config) {
        this.config = config;
    }

    public Score score(Hour hour, List<Alert> alerts) {
        var advisories = new ArrayList<Advisory>();
        var penalties = new ArrayList<Score.Penalty>();
        double total = 0;

        total += temperature(hour, advisories, penalties);
        total += airQuality(hour, advisories, penalties);
        total += ozone(hour, advisories, penalties);
        total += pollen(hour, advisories, penalties);
        total += ultraviolet(hour, advisories, penalties);
        total += precipitation(hour, advisories, penalties);
        total += wind(hour, advisories, penalties);
        total += darkness(hour, advisories, penalties);

        int value = (int) Math.round(clamp(100 - total, 0, 100));

        // A severe weather alert is not just another penalty — it overrides an otherwise
        // pleasant forecast. 72°F and clear is still a bad time to run under a tornado warning.
        for (Alert alert : alerts) {
            if (alert.isSerious() && alert.coversHour(hour.time())) {
                advisories.add(new Advisory.SevereWeather(alert));
                if (value > SEVERE_ALERT_SCORE_CAP) {
                    penalties.add(new Score.Penalty("severe weather", value - SEVERE_ALERT_SCORE_CAP));
                    value = (int) SEVERE_ALERT_SCORE_CAP;
                }
            }
        }

        return new Score(value, advisories, penalties);
    }

    // ------------------------------------------------------------------

    /** Apparent ("feels like") temperature is what the body experiences, so prefer it. */
    private double temperature(Hour hour, List<Advisory> advisories, List<Score.Penalty> penalties) {
        Double feels = hour.apparentF() != null ? hour.apparentF() : hour.tempF();
        if (feels == null) {
            advisories.add(new Advisory.Unknown("temperature"));
            return 0;
        }
        double penalty;
        if (feels > config.idealTempMaxF()) {
            penalty = Math.min((feels - config.idealTempMaxF()) * 2, MAX_TEMP_PENALTY);
            advisories.add(new Advisory.Heat(feels, config.idealTempMaxF()));
        } else if (feels < config.idealTempMinF()) {
            penalty = Math.min((config.idealTempMinF() - feels) * 2, MAX_TEMP_PENALTY);
            advisories.add(new Advisory.Cold(feels, config.idealTempMinF()));
        } else {
            return 0;
        }
        penalties.add(new Score.Penalty("temperature", penalty));
        return penalty;
    }

    /** US AQI bands, following the EPA's own breakpoints. */
    private double airQuality(Hour hour, List<Advisory> advisories, List<Score.Penalty> penalties) {
        Integer aqi = hour.usAqi();
        if (aqi == null) {
            advisories.add(new Advisory.Unknown("air quality"));
            return 0;
        }
        double penalty;
        if (aqi <= 50) penalty = 0;
        else if (aqi <= 100) penalty = 10;
        else if (aqi <= 150) penalty = 30;
        else if (aqi <= 200) penalty = 55;
        else penalty = 80;
        if (penalty > 0) {
            advisories.add(new Advisory.AirQuality(aqi));
            penalties.add(new Score.Penalty("air quality", penalty));
        }
        return penalty;
    }

    /**
     * Ground-level ozone, in µg/m³.
     *
     * <p>Scored separately from the AQI headline because ozone is the factor that most
     * often makes a warm, clear, apparently perfect afternoon a bad time to run: it peaks
     * with sunlight and heat, and a runner moving hard inhales far more of it than the
     * sedentary person the AQI band was written for. It is also the respiratory factor
     * with full US coverage, where the pollen model has none.
     *
     * <p>Thresholds track the EPA 8-hour standard of 70 ppb (~137 µg/m³).
     */
    private double ozone(Hour hour, List<Advisory> advisories, List<Score.Penalty> penalties) {
        Double ugm3 = hour.ozone();
        if (ugm3 == null) return 0;
        double penalty;
        if (ugm3 < 100) penalty = 0;
        else if (ugm3 < 140) penalty = 8;
        else if (ugm3 < 180) penalty = 18;
        else penalty = 30;

        if (penalty > 0) {
            advisories.add(new Advisory.Ozone(ugm3));
            penalties.add(new Score.Penalty("ozone", penalty));
        }
        return penalty;
    }

    /** Scaled by the user's own sensitivity: 0 disables it entirely. */
    private double pollen(Hour hour, List<Advisory> advisories, List<Score.Penalty> penalties) {
        Double grains = hour.totalPollen();
        if (grains == null) {
            advisories.add(new Advisory.Unknown("pollen"));
            return 0;
        }
        if (config.pollenSensitivity() == 0) return 0;

        double base;
        if (grains < 10) base = 0;
        else if (grains < 50) base = 8;
        else if (grains < 150) base = 20;
        else base = 35;

        double penalty = base * config.pollenSensitivity();
        if (penalty > 0) {
            advisories.add(new Advisory.Pollen(grains, hour.dominantPollen()));
            penalties.add(new Score.Penalty("pollen", penalty));
        }
        return penalty;
    }

    private double ultraviolet(Hour hour, List<Advisory> advisories, List<Score.Penalty> penalties) {
        Double uv = hour.uvIndex();
        if (uv == null) return 0;
        double penalty;
        if (uv <= 5) penalty = 0;
        else if (uv <= 7) penalty = 5;
        else if (uv <= 10) penalty = 12;
        else penalty = 20;

        if (penalty > 0) {
            advisories.add(new Advisory.Uv(uv));
            penalties.add(new Score.Penalty("UV", penalty));
        }
        return penalty;
    }

    private double precipitation(Hour hour, List<Advisory> advisories, List<Score.Penalty> penalties) {
        Double chance = hour.precipChance();
        if (chance == null || chance <= 20) return 0;
        double penalty = chance * 0.35;
        advisories.add(new Advisory.Precipitation(chance));
        penalties.add(new Score.Penalty("rain", penalty));
        return penalty;
    }

    private double wind(Hour hour, List<Advisory> advisories, List<Score.Penalty> penalties) {
        Double kph = hour.windKph();
        if (kph == null || kph <= 15) return 0;
        double penalty = Math.min((kph - 15) * 1.2, MAX_WIND_PENALTY);
        advisories.add(new Advisory.Wind(kph));
        penalties.add(new Score.Penalty("wind", penalty));
        return penalty;
    }

    /** A safety penalty rather than a comfort one, so it is modest but never zero. */
    private double darkness(Hour hour, List<Advisory> advisories, List<Score.Penalty> penalties) {
        if (hour.daylight() || !config.avoidDarkness()) return 0;
        advisories.add(new Advisory.Darkness());
        penalties.add(new Score.Penalty("darkness", 12));
        return 12;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
