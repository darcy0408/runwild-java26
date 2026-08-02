package dev.runwild.score;

import dev.runwild.model.Advisory;
import dev.runwild.model.Hour;
import dev.runwild.model.RunWindow;
import dev.runwild.model.Score;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WindowFinderTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 2, 0, 0);

    /** Builds an hourly series straight from a list of scores. */
    private static List<Hour> hoursFor(int count) {
        var hours = new ArrayList<Hour>();
        for (int i = 0; i < count; i++) {
            hours.add(new Hour(START.plusHours(i), 55.0, 55.0, 45.0, 5.0, 0.0, 3.0,
                    30, 5.0, 40.0, 0.0, 0.0, 0.0, true));
        }
        return hours;
    }

    private static List<Score> scoresOf(int... values) {
        var scores = new ArrayList<Score>();
        for (int value : values) scores.add(new Score(value, List.of(), List.of()));
        return scores;
    }

    @Test
    @DisplayName("ranks the obviously best contiguous block first")
    void findsBestBlock() {
        int[] values = {10, 20, 30, 95, 96, 97, 30, 20};
        List<RunWindow> windows =
                WindowFinder.rank(hoursFor(values.length), scoresOf(values), 3, 3);

        assertFalse(windows.isEmpty());
        RunWindow best = windows.getFirst();
        assertEquals(START.plusHours(3), best.start());
        assertEquals(START.plusHours(6), best.end(), "the block runs through the end of its last hour");
        assertEquals(96, best.score());
    }

    @Test
    @DisplayName("prefers a flat block over a spiky one with the same average")
    void worstHourBreaksTheAverage() {
        // Block A (indices 0-2): 100, 40, 100  -> average 80, worst 40 -> 68
        // Block B (indices 4-6): 78,  80, 82   -> average 80, worst 78 -> 79.4
        int[] values = {100, 40, 100, 0, 78, 80, 82};
        List<RunWindow> windows =
                WindowFinder.rank(hoursFor(values.length), scoresOf(values), 3, 3);

        RunWindow best = windows.getFirst();
        assertEquals(START.plusHours(4), best.start(),
                "an hour you would genuinely hate should sink the block that contains it");
        assertTrue(best.worst() >= 78);
    }

    @Test
    @DisplayName("returns nothing when the forecast is shorter than the run")
    void horizonTooShort() {
        assertTrue(WindowFinder.rank(hoursFor(2), scoresOf(90, 90), 3, 3).isEmpty());
        assertTrue(WindowFinder.rank(List.of(), List.of(), 1, 3).isEmpty());
    }

    @Test
    @DisplayName("a single-hour run window still works")
    void singleHourWindow() {
        int[] values = {10, 90, 20};
        List<RunWindow> windows =
                WindowFinder.rank(hoursFor(values.length), scoresOf(values), 1, 3);

        assertEquals(3, windows.size());
        assertEquals(90, windows.getFirst().score());
        assertEquals(START.plusHours(1), windows.getFirst().start());
    }

    @Test
    @DisplayName("respects the requested number of results")
    void limitIsHonoured() {
        int[] values = {50, 60, 70, 80, 90, 40, 30, 20};
        assertEquals(2, WindowFinder.rank(hoursFor(values.length), scoresOf(values), 2, 2).size());
    }

    @Test
    @DisplayName("mismatched inputs fail loudly rather than scoring the wrong hour")
    void mismatchedInputsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> WindowFinder.rank(hoursFor(5), scoresOf(90, 90), 2, 3));
    }

    @Test
    @DisplayName("collapses repeated advisories to one entry per kind, keeping the worst")
    void advisoriesAreMergedNotRepeated() {
        var hours = hoursFor(3);
        var scores = List.of(
                new Score(60, List.of(new Advisory.Precipitation(40)), List.of()),
                new Score(50, List.of(new Advisory.Precipitation(90)), List.of()),
                new Score(55, List.of(new Advisory.Precipitation(70)), List.of()));

        RunWindow window = WindowFinder.rank(hours, scores, 3, 1).getFirst();

        assertEquals(1, window.advisories().size(), "three rain warnings should collapse to one");
        assertEquals(new Advisory.Precipitation(90), window.advisories().getFirst(),
                "the worst instance is the one worth reporting");
    }

    @Test
    @DisplayName("coverage gaps are not repeated under every window")
    void unknownAdvisoriesExcludedFromWindows() {
        var hours = hoursFor(2);
        var scores = List.of(
                new Score(90, List.of(new Advisory.Unknown("pollen")), List.of()),
                new Score(90, List.of(new Advisory.Unknown("pollen")), List.of()));

        RunWindow window = WindowFinder.rank(hours, scores, 2, 1).getFirst();
        assertTrue(window.advisories().isEmpty(),
                "'no pollen data here' is a fact about the location, not about this window");
    }
}
