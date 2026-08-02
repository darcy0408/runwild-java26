package dev.runwild.cli;

import dev.runwild.model.Advisory;
import dev.runwild.model.Alert;
import dev.runwild.model.Plan;
import dev.runwild.model.RunWindow;
import dev.runwild.model.Verdict;

import java.time.format.DateTimeFormatter;

/** Renders a {@link Plan} as a terminal report. */
public final class Cli {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("ha");
    private static final int BAR_WIDTH = 24;

    // Honour NO_COLOR (https://no-color.org) and non-tty output.
    private static final boolean COLOR =
            System.getenv("NO_COLOR") == null && System.console() != null;

    private Cli() {}

    public static void render(Plan plan) {
        System.out.println();
        System.out.println(bold("  RunWild — " + plan.locationName()));
        System.out.println(dim("  " + plan.generatedAt()
                .format(DateTimeFormatter.ofPattern("EEEE d MMMM, h:mm a"))));
        System.out.println();

        renderAlerts(plan);
        renderVerdict(plan);
        renderWindows(plan);
        renderStrip(plan);
        renderTelemetry(plan);
    }

    private static void renderAlerts(Plan plan) {
        for (Alert alert : plan.alerts()) {
            String mark = alert.isSerious() ? "⚠ " : "ⓘ ";
            String tint = alert.isSerious() ? "31" : "33";
            System.out.println("  " + color(tint, mark + alert.event())
                    + dim(" (" + alert.severity() + ")") + " — " + shorten(alert.headline(), 90));
        }
        if (!plan.alerts().isEmpty()) System.out.println();
    }

    private static void renderVerdict(Plan plan) {
        var best = plan.best();
        if (best.isEmpty()) {
            System.out.println("  No full " + "run window" + " available in the forecast horizon.");
            return;
        }
        RunWindow window = best.get();
        Verdict verdict = window.verdict();

        String headline = plan.bestIsNow()
                ? "GO NOW"
                : "BEST WINDOW: " + window.describe(plan.generatedAt()).toUpperCase();

        System.out.println("  " + bold(color(colorFor(verdict), headline)));
        System.out.println("  " + scoreBar(window.score()) + "  " + window.score() + "/100  "
                + dim("(" + verdict.label() + ")"));
        System.out.println("  " + verdict.advice());
        System.out.println();
    }

    private static void renderWindows(Plan plan) {
        if (plan.windows().isEmpty()) return;
        System.out.println(bold("  Best windows"));
        int rank = 1;
        for (RunWindow window : plan.windows()) {
            System.out.printf("   %d. %-26s %3d/100  %s%n",
                    rank++,
                    window.describe(plan.generatedAt()),
                    window.score(),
                    dim("(" + window.verdict().label() + ")"));
            for (Advisory advisory : window.advisories()) {
                System.out.println("      · " + Advisory.humanize(advisory));
            }
        }
        System.out.println();
    }

    /** One row per hour for the next stretch — the shape of the day at a glance. */
    private static void renderStrip(Plan plan) {
        System.out.println(bold("  Next 24 hours"));
        plan.hours().stream().limit(24).forEach(hourScore -> {
            var hour = hourScore.hour();
            var score = hourScore.score();
            System.out.printf("   %-5s %s %3d  %s%n",
                    hour.time().format(HOUR).toLowerCase(),
                    scoreBar(score.value()),
                    score.value(),
                    dim(shorten(score.headline())));
        });
        System.out.println();
    }

    private static void renderTelemetry(Plan plan) {
        var telemetry = plan.telemetry();
        System.out.println(dim("  %d sources fetched concurrently in %d ms · %s"
                .formatted(telemetry.alertsAvailable() ? 3 : 2,
                        telemetry.totalMillis(),
                        telemetry.protocolSummary())));
        if (!telemetry.alertsAvailable()) {
            System.out.println(dim("  severe-weather alerts unavailable — advice excludes them"));
        }
        for (String note : plan.coverageNotes()) {
            System.out.println(dim("  " + note));
        }
        System.out.println();
    }

    private static String scoreBar(int score) {
        int filled = Math.round(score / 100f * BAR_WIDTH);
        String bar = "█".repeat(filled) + dim("·".repeat(BAR_WIDTH - filled));
        return color(colorFor(Verdict.of(score)), bar);
    }

    private static String colorFor(Verdict verdict) {
        return switch (verdict) {
            case IDEAL -> "92";
            case GOOD -> "32";
            case FAIR -> "33";
            case POOR -> "31";
            case STAY_IN -> "91";
        };
    }

    private static String shorten(String text) {
        return shorten(text, 46);
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static String color(String code, String text) {
        return COLOR ? "[" + code + "m" + text + "[0m" : text;
    }

    private static String bold(String text) {
        return COLOR ? "[1m" + text + "[0m" : text;
    }

    private static String dim(String text) {
        return COLOR ? "[2m" + text + "[0m" : text;
    }
}
