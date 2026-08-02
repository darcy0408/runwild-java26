package dev.runwild.model;

import java.time.LocalDateTime;

/**
 * One hour of merged conditions from all upstream sources.
 *
 * <p>Boxed types are used for every value that a source may legitimately not supply.
 * {@code null} means "we do not know", which is <em>not</em> the same as zero — an
 * unknown AQI must never be scored as perfect air. Callers must go through the accessor
 * helpers rather than unboxing directly.
 *
 * @param time      local time of the hour, as returned by the upstream API (timezone=auto)
 * @param daylight  whether this hour falls between sunrise and sunset
 */
public record Hour(
        LocalDateTime time,
        Double tempF,
        Double apparentF,
        Double humidity,
        Double windKph,
        Double precipChance,
        Double uvIndex,
        Integer usAqi,
        Double pm25,
        Double ozone,
        Double ragweedPollen,
        Double grassPollen,
        Double birchPollen,
        boolean daylight) {

    /**
     * Combined pollen load in grains/m³, or {@code null} if no pollen species was
     * reported for this location. Open-Meteo's pollen coverage is regional; treating a
     * gap as "zero pollen" would produce confidently wrong advice.
     */
    public Double totalPollen() {
        if (ragweedPollen == null && grassPollen == null && birchPollen == null) {
            return null;
        }
        return zero(ragweedPollen) + zero(grassPollen) + zero(birchPollen);
    }

    /** The dominant pollen species this hour, for explaining the score. */
    public String dominantPollen() {
        double r = zero(ragweedPollen), g = zero(grassPollen), b = zero(birchPollen);
        double max = Math.max(r, Math.max(g, b));
        if (max <= 0) return null;
        if (max == r) return "ragweed";
        if (max == g) return "grass";
        return "birch";
    }

    private static double zero(Double d) {
        return d == null ? 0.0 : d;
    }
}
