package dev.runwild.score;

import dev.runwild.config.Config;
import dev.runwild.model.Advisory;
import dev.runwild.model.Alert;
import dev.runwild.model.Hour;
import dev.runwild.model.Score;
import dev.runwild.model.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScorerTest {

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 8, 2, 12, 0);
    private final Config config = Config.defaults();   // ideal 40-65°F, pollen sensitivity 1.0
    private final Scorer scorer = new Scorer(config);

    /** A pleasant hour with every source reporting: nothing to deduct. */
    private static Hour perfectHour() {
        return new Hour(NOON, 55.0, 55.0, 45.0, 5.0, 0.0, 3.0,
                30, 5.0, 40.0, 0.0, 0.0, 0.0, true);
    }

    private static Hour with(Hour base, java.util.function.UnaryOperator<Hour> change) {
        return change.apply(base);
    }

    @Test
    @DisplayName("a perfect hour scores 100 with no advisories")
    void perfectHourScoresFull() {
        Score score = scorer.score(perfectHour(), List.of());
        assertEquals(100, score.value());
        assertTrue(score.advisories().isEmpty(), "expected no advisories, got " + score.advisories());
        assertEquals(Verdict.IDEAL, score.verdict());
    }

    @Test
    @DisplayName("heat above the user's ceiling costs 2 points per degree, capped at 40")
    void heatIsPenalisedAndCapped() {
        Hour warm = new Hour(NOON, 75.0, 75.0, 45.0, 5.0, 0.0, 3.0, 30, 5.0, 40.0, 0.0, 0.0, 0.0, true);
        // 75°F is 10° above the 65° ceiling -> 20 points
        assertEquals(80, scorer.score(warm, List.of()).value());

        Hour scorching = new Hour(NOON, 130.0, 130.0, 45.0, 5.0, 0.0, 3.0, 30, 5.0, 40.0, 0.0, 0.0, 0.0, true);
        // 65° above the ceiling would be 130 points; the cap holds it to 40
        assertEquals(60, scorer.score(scorching, List.of()).value());
    }

    @Test
    @DisplayName("cold below the user's floor is penalised symmetrically")
    void coldIsPenalised() {
        Hour cold = new Hour(NOON, 30.0, 30.0, 45.0, 5.0, 0.0, 3.0, 30, 5.0, 40.0, 0.0, 0.0, 0.0, true);
        Score score = scorer.score(cold, List.of());
        assertEquals(80, score.value());   // 10° below the 40° floor -> 20 points
        assertTrue(score.advisories().stream().anyMatch(a -> a instanceof Advisory.Cold));
    }

    @Test
    @DisplayName("apparent temperature wins over the raw reading")
    void apparentTemperaturePreferred() {
        // 60°F on the thermometer sits inside the ideal band and would score 100...
        Hour comfortable = new Hour(NOON, 60.0, 60.0, 45.0, 5.0, 0.0, 3.0, 30, 5.0, 40.0, 0.0, 0.0, 0.0, true);
        assertEquals(100, scorer.score(comfortable, List.of()).value());

        // ...but the same 60°F air at 90% humidity feels like 80°F, and that is what the
        // body experiences: 15° over the ceiling -> 30 points.
        Hour humid = new Hour(NOON, 60.0, 80.0, 90.0, 5.0, 0.0, 3.0, 30, 5.0, 40.0, 0.0, 0.0, 0.0, true);
        Score score = scorer.score(humid, List.of());
        assertEquals(70, score.value());
        assertTrue(score.advisories().stream().anyMatch(a -> a instanceof Advisory.Heat));
    }

    @Test
    @DisplayName("AQI is scored in EPA bands")
    void airQualityBands() {
        assertEquals(100, scorer.score(aqi(50), List.of()).value());   // good: no penalty
        assertEquals(90, scorer.score(aqi(100), List.of()).value());   // moderate: 10
        assertEquals(70, scorer.score(aqi(150), List.of()).value());   // sensitive groups: 30
        assertEquals(45, scorer.score(aqi(200), List.of()).value());   // unhealthy: 55
        assertEquals(20, scorer.score(aqi(300), List.of()).value());   // hazardous: 80
    }

    private static Hour aqi(int value) {
        return new Hour(NOON, 55.0, 55.0, 45.0, 5.0, 0.0, 3.0, value, 5.0, 40.0, 0.0, 0.0, 0.0, true);
    }

    @Test
    @DisplayName("missing data is never scored as good data")
    void missingAirQualityIsFlaggedNotAssumedClean() {
        Hour noAqi = new Hour(NOON, 55.0, 55.0, 45.0, 5.0, 0.0, 3.0,
                null, null, null, null, null, null, true);
        Score score = scorer.score(noAqi, List.of());

        assertEquals(100, score.value(), "unknown factors must not invent a penalty");
        assertTrue(score.advisories().stream()
                        .anyMatch(a -> a instanceof Advisory.Unknown u && u.factor().equals("air quality")),
                "an optimistic score must say why it might be wrong");
        assertTrue(score.advisories().stream()
                .anyMatch(a -> a instanceof Advisory.Unknown u && u.factor().equals("pollen")));
    }

    @Test
    @DisplayName("pollen penalty scales with the user's own sensitivity")
    void pollenScalesWithSensitivity() {
        Hour pollenHeavy = new Hour(NOON, 55.0, 55.0, 45.0, 5.0, 0.0, 3.0,
                30, 5.0, 40.0, 100.0, 60.0, 0.0, true);   // 160 grains total

        Config insensitive = new Config("t", 0, 0, 40, 65, 0.0, 1, 2, true);
        Config typical = new Config("t", 0, 0, 40, 65, 1.0, 1, 2, true);
        Config severe = new Config("t", 0, 0, 40, 65, 2.0, 1, 2, true);

        int none = new Scorer(insensitive).score(pollenHeavy, List.of()).value();
        int normal = new Scorer(typical).score(pollenHeavy, List.of()).value();
        int bad = new Scorer(severe).score(pollenHeavy, List.of()).value();

        assertEquals(100, none, "sensitivity 0 should ignore pollen entirely");
        assertEquals(65, normal, "160 grains -> 35 points at sensitivity 1.0");
        assertEquals(30, bad, "the same air should be twice as costly at sensitivity 2.0");
        assertTrue(bad < normal && normal < none, "penalty must increase with sensitivity");
    }

    @Test
    @DisplayName("ozone is scored separately from the AQI headline")
    void ozoneIsScored() {
        Hour ozoneHeavy = new Hour(NOON, 55.0, 55.0, 45.0, 5.0, 0.0, 3.0,
                30, 5.0, 200.0, 0.0, 0.0, 0.0, true);
        Score score = scorer.score(ozoneHeavy, List.of());
        assertEquals(70, score.value());   // >180 µg/m³ -> 30 points
        assertTrue(score.advisories().stream().anyMatch(a -> a instanceof Advisory.Ozone));
    }

    @Test
    @DisplayName("darkness is a safety penalty, and can be switched off")
    void darknessPenalty() {
        Hour night = new Hour(NOON, 55.0, 55.0, 45.0, 5.0, 0.0, 0.0,
                30, 5.0, 40.0, 0.0, 0.0, 0.0, false);
        assertEquals(88, scorer.score(night, List.of()).value());

        Config nightOwl = new Config("t", 0, 0, 40, 65, 1.0, 1, 2, false);
        assertEquals(100, new Scorer(nightOwl).score(night, List.of()).value());
    }

    @Test
    @DisplayName("a severe alert caps an otherwise perfect hour")
    void severeAlertOverridesEverything() {
        Alert tornado = new Alert("Tornado Warning", "Extreme",
                "Take shelter now", NOON.minusHours(1), NOON.plusHours(2));

        Score score = scorer.score(perfectHour(), List.of(tornado));

        assertEquals(20, score.value(), "72°F and clear is still no time to run under a tornado warning");
        assertEquals(Verdict.STAY_IN, score.verdict());
        assertTrue(score.advisories().stream().anyMatch(a -> a instanceof Advisory.SevereWeather));
    }

    @Test
    @DisplayName("a minor alert, or one that has expired, does not cap the score")
    void nonApplicableAlertsAreIgnored() {
        Alert minor = new Alert("Frost Advisory", "Minor", "Cover your plants", null, null);
        assertEquals(100, scorer.score(perfectHour(), List.of(minor)).value());

        Alert expired = new Alert("Severe Thunderstorm Warning", "Severe", "Was rough",
                NOON.minusHours(5), NOON.minusHours(3));
        assertEquals(100, scorer.score(perfectHour(), List.of(expired)).value(),
                "an alert that lapsed before this hour must not affect it");
    }

    @Test
    @DisplayName("penalties are itemised so the recommendation can be interrogated")
    void penaltiesAreItemised() {
        Hour rough = new Hour(NOON, 80.0, 80.0, 45.0, 30.0, 60.0, 9.0,
                120, 20.0, 150.0, 0.0, 0.0, 0.0, true);
        Score score = scorer.score(rough, List.of());

        var factors = score.penalties().stream().map(Score.Penalty::factor).toList();
        assertTrue(factors.containsAll(List.of("temperature", "air quality", "ozone", "UV", "rain", "wind")),
                "expected every triggered factor to be itemised, got " + factors);
        assertTrue(score.penalties().stream().allMatch(p -> p.points() > 0));
    }

    @Test
    @DisplayName("scores never leave the 0-100 range no matter how bad it gets")
    void scoreIsClamped() {
        Hour apocalypse = new Hour(NOON, 130.0, 130.0, 100.0, 90.0, 100.0, 12.0,
                500, 400.0, 400.0, 300.0, 300.0, 300.0, false);
        int value = scorer.score(apocalypse, List.of()).value();
        assertTrue(value >= 0 && value <= 100, "score out of range: " + value);
        assertEquals(Verdict.STAY_IN, Verdict.of(value));
    }
}
