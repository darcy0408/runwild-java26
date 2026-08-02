package dev.runwild.model;

import java.util.Comparator;
import java.util.List;

/**
 * The score for a single hour, with the reasons that produced it.
 *
 * <p>{@code penalties} is deliberately itemised rather than collapsed into the number:
 * a recommendation the user cannot interrogate is a recommendation they will not trust.
 *
 * @param value      0-100, higher is better
 * @param advisories why it is not 100, worst first
 * @param penalties  factor name to points deducted, for the UI breakdown
 */
public record Score(int value, List<Advisory> advisories, List<Penalty> penalties) {

    public record Penalty(String factor, double points) {}

    public Score {
        if (value < 0 || value > 100) throw new IllegalArgumentException("score out of range: " + value);
        advisories = List.copyOf(advisories).stream()
                .sorted(Comparator.comparingInt(Advisory::severity).reversed())
                .toList();
        penalties = List.copyOf(penalties);
    }

    public Verdict verdict() {
        return Verdict.of(value);
    }

    /** The single most important thing to tell the user about this hour. */
    public String headline() {
        return advisories.isEmpty()
                ? "Nothing standing in your way."
                : Advisory.humanize(advisories.getFirst());
    }
}
