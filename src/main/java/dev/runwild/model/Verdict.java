package dev.runwild.model;

/** Plain-language banding of a 0-100 score. */
public enum Verdict {

    IDEAL("Ideal", "Conditions do not get better than this."),
    GOOD("Good", "A good time to run."),
    FAIR("Fair", "Runnable, with a couple of things to plan around."),
    POOR("Poor", "Doable, but you will feel it."),
    STAY_IN("Stay in", "Not worth it — take this one indoors.");

    private final String label;
    private final String advice;

    Verdict(String label, String advice) {
        this.label = label;
        this.advice = advice;
    }

    public String label() { return label; }

    public String advice() { return advice; }

    public static Verdict of(int score) {
        if (score >= 85) return IDEAL;
        if (score >= 70) return GOOD;
        if (score >= 50) return FAIR;
        if (score >= 30) return POOR;
        return STAY_IN;
    }
}
