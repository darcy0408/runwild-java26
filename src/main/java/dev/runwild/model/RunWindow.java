package dev.runwild.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A contiguous block of hours long enough for the user's run, with a blended score.
 *
 * @param score   blend of the block's average and its worst hour
 * @param worst   the worst single hour in the block, which is what you actually feel
 */
public record RunWindow(
        LocalDateTime start,
        LocalDateTime end,
        int score,
        int worst,
        List<Advisory> advisories) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("EEE h:mm a");

    public Verdict verdict() {
        return Verdict.of(score);
    }

    /** "Today 6:00 PM – 7:00 PM" / "Sun 6:00 AM – 7:00 AM" */
    public String describe(LocalDateTime now) {
        boolean today = start.toLocalDate().equals(now.toLocalDate());
        boolean tomorrow = start.toLocalDate().equals(now.toLocalDate().plusDays(1));
        String prefix = today ? "Today " : tomorrow ? "Tomorrow " : "";
        String startText = (today || tomorrow) ? start.format(TIME) : start.format(DAY_TIME);
        return "%s%s – %s".formatted(prefix, startText, end.format(TIME));
    }

    public String summary() {
        return advisories.isEmpty()
                ? "Clear on every factor."
                : Advisory.humanize(advisories.getFirst());
    }
}
