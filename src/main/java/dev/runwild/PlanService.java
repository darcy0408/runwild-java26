package dev.runwild;

import dev.runwild.config.Config;
import dev.runwild.fetch.DataFetcher;
import dev.runwild.model.Plan;
import dev.runwild.model.Score;
import dev.runwild.score.Scorer;
import dev.runwild.score.WindowFinder;

import java.time.LocalDateTime;
import java.util.List;

/** Fetches, scores, and ranks — the one call that produces a {@link Plan}. */
public final class PlanService {

    private static final int WINDOWS_TO_RETURN = 3;

    private final DataFetcher fetcher = new DataFetcher();

    public Plan build() throws Exception {
        Config config = RunContext.current();
        var bundle = fetcher.fetch(config);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thisHour = now.withMinute(0).withSecond(0).withNano(0);

        // Hours already past are noise: nobody can run at 6am yesterday.
        var upcoming = bundle.hours().stream()
                .filter(hour -> !hour.time().isBefore(thisHour))
                .toList();
        // If the upstream series is entirely stale, show it rather than an empty page.
        if (upcoming.isEmpty()) upcoming = bundle.hours();

        var scorer = new Scorer(config);
        List<Score> scores = upcoming.stream()
                .map(hour -> scorer.score(hour, bundle.alerts()))
                .toList();

        var windows = WindowFinder.rank(
                upcoming, scores, config.runDurationHours(), WINDOWS_TO_RETURN);

        var hourScores = new java.util.ArrayList<Plan.HourScore>(upcoming.size());
        for (int i = 0; i < upcoming.size(); i++) {
            hourScores.add(new Plan.HourScore(upcoming.get(i), scores.get(i)));
        }

        return new Plan(
                config.locationName(),
                now,
                List.copyOf(hourScores),
                windows,
                bundle.alerts(),
                coverageNotes(upcoming),
                bundle.telemetry());
    }

    /**
     * Which factors have no data at all here.
     *
     * <p>Open-Meteo's pollen model covers Europe only, so a US user gets nulls for every
     * pollen species. Saying so once, plainly, is better than either silently scoring
     * missing pollen as zero (confidently wrong) or repeating a warning on every hour.
     */
    private static List<String> coverageNotes(List<dev.runwild.model.Hour> hours) {
        var notes = new java.util.ArrayList<String>();
        if (hours.stream().allMatch(hour -> hour.totalPollen() == null)) {
            notes.add("No pollen forecast covers this location, so pollen is excluded "
                    + "from the score. Ground-level ozone and the US air-quality index "
                    + "are scored instead.");
        }
        if (hours.stream().allMatch(hour -> hour.usAqi() == null)) {
            notes.add("No air-quality index available for this location.");
        }
        if (hours.stream().allMatch(hour -> hour.ozone() == null)) {
            notes.add("No ozone forecast available for this location.");
        }
        return List.copyOf(notes);
    }
}
