package dev.runwild.model;

/**
 * A specific, named reason a run window is less than perfect.
 *
 * <p>Sealed so that every consumer can switch exhaustively without a {@code default}
 * branch. When a new advisory is added here the compiler immediately points at every
 * place that must learn to explain it — which is exactly the safety net a scoring engine
 * that grows over time wants.
 */
public sealed interface Advisory {

    /** Ordering hint for display: the biggest problem should be read first. */
    int severity();

    record Heat(double apparentF, double idealMaxF) implements Advisory {
        public int severity() { return (int) Math.min(100, (apparentF - idealMaxF) * 3); }
    }

    record Cold(double apparentF, double idealMinF) implements Advisory {
        public int severity() { return (int) Math.min(100, (idealMinF - apparentF) * 3); }
    }

    record AirQuality(int usAqi) implements Advisory {
        public int severity() { return Math.min(100, Math.max(0, (usAqi - 50) / 2)); }
    }

    record Ozone(double microgramsPerCubicMetre) implements Advisory {
        public int severity() { return (int) Math.min(100, (microgramsPerCubicMetre - 100) / 1.5); }
    }

    record Pollen(double grains, String species) implements Advisory {
        public int severity() { return (int) Math.min(100, grains / 2); }
    }

    record Uv(double index) implements Advisory {
        public int severity() { return (int) Math.min(100, (index - 5) * 10); }
    }

    record Precipitation(double chancePercent) implements Advisory {
        public int severity() { return (int) chancePercent; }
    }

    record Wind(double kph) implements Advisory {
        public int severity() { return (int) Math.min(100, (kph - 15) * 3); }
    }

    record Darkness() implements Advisory {
        public int severity() { return 40; }
    }

    record SevereWeather(Alert alert) implements Advisory {
        public int severity() { return 100; }
    }

    /** Data was missing, so the score for this factor is a guess. Stated, never hidden. */
    record Unknown(String factor) implements Advisory {
        public int severity() { return 5; }
    }

    /**
     * Human-readable coaching line. Exhaustive switch with record deconstruction — no
     * {@code default} branch, so adding an Advisory above is a compile error until it is
     * explained here.
     */
    static String humanize(Advisory advisory) {
        return switch (advisory) {
            case Heat(double f, double ideal) ->
                    "Feels like %.0f°F, above your %.0f°F ceiling — hydrate and ease the pace"
                            .formatted(f, ideal);
            case Cold(double f, double ideal) ->
                    "Feels like %.0f°F, below your %.0f°F floor — layer up and cover your extremities"
                            .formatted(f, ideal);
            case AirQuality(int aqi) when aqi > 150 ->
                    "AQI %d — unhealthy; move the run indoors".formatted(aqi);
            case AirQuality(int aqi) ->
                    "AQI %d — you will be breathing this hard for the whole run".formatted(aqi);
            case Ozone(double ugm3) when ugm3 > 180 ->
                    "Ozone %.0f µg/m³ — high; this is why afternoon runs burn your throat"
                            .formatted(ugm3);
            case Ozone(double ugm3) ->
                    "Ozone %.0f µg/m³ — noticeable on hard efforts".formatted(ugm3);
            case Pollen(double grains, String species) ->
                    "%.0f grains/m³ of %s — take an antihistamine before you head out"
                            .formatted(grains, species);
            case Uv(double index) ->
                    "UV index %.1f — sunscreen, hat, and avoid the exposed stretches".formatted(index);
            case Precipitation(double chance) ->
                    "%.0f%% chance of rain".formatted(chance);
            case Wind(double kph) ->
                    "Wind %.0f km/h — run into it on the way out so it pushes you home".formatted(kph);
            case Darkness() ->
                    "It will be dark — reflective gear and a headlamp";
            case SevereWeather(Alert alert) ->
                    "⚠ %s: %s".formatted(alert.event(), alert.headline());
            case Unknown(String factor) ->
                    "No %s data for this location — scored optimistically".formatted(factor);
        };
    }

    /** Short label for compact UI chips. */
    static String label(Advisory advisory) {
        return switch (advisory) {
            case Heat _            -> "heat";
            case Cold _            -> "cold";
            case AirQuality _      -> "air quality";
            case Ozone _           -> "ozone";
            case Pollen _          -> "pollen";
            case Uv _              -> "UV";
            case Precipitation _   -> "rain";
            case Wind _            -> "wind";
            case Darkness _        -> "dark";
            case SevereWeather _   -> "alert";
            case Unknown _         -> "unknown";
        };
    }
}
