package dev.runwild.model;

import java.time.LocalDateTime;

/**
 * An active National Weather Service alert.
 *
 * @param event    e.g. "Heat Advisory", "Severe Thunderstorm Warning"
 * @param severity NWS scale: Extreme, Severe, Moderate, Minor, Unknown
 * @param onset    when it starts applying, may be null
 * @param expires  when it lapses, may be null
 */
public record Alert(
        String event,
        String severity,
        String headline,
        LocalDateTime onset,
        LocalDateTime expires) {

    /** Only Severe and Extreme alerts should override an otherwise good forecast. */
    public boolean isSerious() {
        return "Extreme".equalsIgnoreCase(severity) || "Severe".equalsIgnoreCase(severity);
    }

    /** Whether this alert is in force during the given hour. */
    public boolean coversHour(LocalDateTime hour) {
        if (onset != null && hour.isBefore(onset)) return false;
        if (expires != null && hour.isAfter(expires)) return false;
        return true;
    }
}
