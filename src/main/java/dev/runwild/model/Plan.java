package dev.runwild.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The complete answer to "should I run outside?" — every scored hour, the best windows,
 * any active alerts, and how the data was fetched.
 */
public record Plan(
        String locationName,
        LocalDateTime generatedAt,
        List<HourScore> hours,
        List<RunWindow> windows,
        List<Alert> alerts,
        /** Factors with no upstream data at this location, stated rather than hidden. */
        List<String> coverageNotes,
        Telemetry telemetry) {

    public record HourScore(Hour hour, Score score) {}

    /** The best window available, if the forecast horizon contained a full one. */
    public Optional<RunWindow> best() {
        return windows.isEmpty() ? Optional.empty() : Optional.of(windows.getFirst());
    }

    /** Conditions right now — the first hour at or after the current time. */
    public Optional<HourScore> currentHour() {
        return hours.stream()
                .filter(h -> !h.hour().time().isBefore(generatedAt.withMinute(0).withSecond(0).withNano(0)))
                .findFirst();
    }

    /**
     * Whether the best window is the one happening now, which is the difference between
     * telling the user "go now" and "wait until 6pm".
     */
    public boolean bestIsNow() {
        return best().flatMap(window -> currentHour()
                        .map(current -> !window.start().isAfter(current.hour().time())))
                .orElse(false);
    }
}
