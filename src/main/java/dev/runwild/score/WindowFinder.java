package dev.runwild.score;

import dev.runwild.model.Advisory;
import dev.runwild.model.Hour;
import dev.runwild.model.RunWindow;
import dev.runwild.model.Score;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Gatherers;
import java.util.stream.IntStream;

/**
 * Finds the best contiguous block of hours to run in.
 *
 * <p>The user does not run for an instant, they run for an hour or two, so the useful
 * answer is the best <em>block</em> rather than the best single hour. Sliding a window
 * across the scored series is exactly what {@link Gatherers#windowSliding(int)} does, so
 * the search is one stream operation instead of a hand-rolled index loop.
 *
 * <p>A block is scored as 70% its average and 30% its worst hour. Pure averaging happily
 * recommends a window containing one genuinely miserable hour as long as its neighbours
 * are pleasant; weighting the worst hour makes the ranking prefer a flat, reliably
 * decent window over a spiky one with a better mean.
 */
public final class WindowFinder {

    private static final double AVERAGE_WEIGHT = 0.7;
    private static final double WORST_WEIGHT = 0.3;
    private static final int MAX_ADVISORIES_PER_WINDOW = 3;

    private WindowFinder() {}

    public static List<RunWindow> rank(List<Hour> hours, List<Score> scores,
                                       int blockHours, int limit) {
        if (hours.size() != scores.size()) {
            throw new IllegalArgumentException(
                    "hours (%d) and scores (%d) must be the same length"
                            .formatted(hours.size(), scores.size()));
        }
        // At the far end of the forecast horizon there may not be a full block left.
        if (blockHours < 1 || hours.size() < blockHours) return List.of();

        return IntStream.range(0, hours.size()).boxed()
                .gather(Gatherers.windowSliding(blockHours))
                .map(window -> toWindow(window, hours, scores))
                .sorted(Comparator.comparingInt(RunWindow::score).reversed()
                        .thenComparing(RunWindow::start))
                .limit(limit)
                .toList();
    }

    private static RunWindow toWindow(List<Integer> window, List<Hour> hours, List<Score> scores) {
        double average = window.stream().mapToInt(i -> scores.get(i).value()).average().orElse(0);
        int worst = window.stream().mapToInt(i -> scores.get(i).value()).min().orElse(0);
        int blended = (int) Math.round(average * AVERAGE_WEIGHT + worst * WORST_WEIGHT);

        Hour first = hours.get(window.getFirst());
        Hour last = hours.get(window.getLast());

        return new RunWindow(
                first.time(),
                last.time().plusHours(1),   // the block covers through the end of its last hour
                blended,
                worst,
                mergeAdvisories(window, scores));
    }

    /**
     * One entry per distinct kind of problem across the block, keeping the worst instance
     * of each. Repeating "78% chance of rain" for every hour in the window is noise; the
     * user wants to know it will rain, once.
     */
    private static List<Advisory> mergeAdvisories(List<Integer> window, List<Score> scores) {
        var worstByKind = new LinkedHashMap<String, Advisory>();
        for (int index : window) {
            for (Advisory advisory : scores.get(index).advisories()) {
                // "No pollen data for this location" is a property of the whole plan, not
                // of one window. Repeating it under every window is noise; it is reported
                // once as a coverage note instead.
                if (advisory instanceof Advisory.Unknown) continue;
                worstByKind.merge(Advisory.label(advisory), advisory,
                        (existing, candidate) ->
                                candidate.severity() > existing.severity() ? candidate : existing);
            }
        }
        return worstByKind.values().stream()
                .sorted(Comparator.comparingInt(Advisory::severity).reversed())
                .limit(MAX_ADVISORIES_PER_WINDOW)
                .toList();
    }
}
