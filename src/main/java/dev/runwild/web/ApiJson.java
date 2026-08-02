package dev.runwild.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.runwild.model.Advisory;
import dev.runwild.model.Alert;
import dev.runwild.model.Plan;
import dev.runwild.model.RunWindow;

import java.time.format.DateTimeFormatter;

/**
 * Serialises a {@link Plan} for the dashboard.
 *
 * <p>Written by hand rather than reflected over the records: the wire format is a
 * deliberate API surface, and the domain model contains a sealed interface whose
 * polymorphism would otherwise need type-id annotations leaking presentation concerns
 * back into the model. Explicit is also easier to keep stable as the model evolves.
 */
final class ApiJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("h a");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("EEE");

    private ApiJson() {}

    static String write(Plan plan) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("location", plan.locationName());
        root.put("generatedAt", plan.generatedAt().format(ISO));
        root.put("bestIsNow", plan.bestIsNow());

        ArrayNode windows = root.putArray("windows");
        for (RunWindow window : plan.windows()) {
            ObjectNode node = windows.addObject();
            node.put("start", window.start().format(ISO));
            node.put("end", window.end().format(ISO));
            node.put("label", window.describe(plan.generatedAt()));
            node.put("score", window.score());
            node.put("worst", window.worst());
            node.put("verdict", window.verdict().label());
            node.put("advice", window.verdict().advice());
            writeAdvisories(node.putArray("advisories"), window.advisories());
        }

        ArrayNode hours = root.putArray("hours");
        for (Plan.HourScore hourScore : plan.hours()) {
            var hour = hourScore.hour();
            var score = hourScore.score();
            ObjectNode node = hours.addObject();
            node.put("time", hour.time().format(ISO));
            node.put("hourLabel", hour.time().format(HOUR_LABEL));
            node.put("dayLabel", hour.time().format(DAY_LABEL));
            node.put("score", score.value());
            node.put("verdict", score.verdict().label());
            node.put("headline", score.headline());
            node.put("daylight", hour.daylight());
            putNullable(node, "tempF", hour.tempF());
            putNullable(node, "apparentF", hour.apparentF());
            putNullable(node, "humidity", hour.humidity());
            putNullable(node, "windKph", hour.windKph());
            putNullable(node, "precipChance", hour.precipChance());
            putNullable(node, "uvIndex", hour.uvIndex());
            putNullable(node, "ozone", hour.ozone());
            putNullable(node, "pm25", hour.pm25());
            putNullable(node, "pollen", hour.totalPollen());
            if (hour.usAqi() == null) node.putNull("aqi"); else node.put("aqi", hour.usAqi());

            ArrayNode penalties = node.putArray("penalties");
            score.penalties().forEach(penalty -> {
                ObjectNode p = penalties.addObject();
                p.put("factor", penalty.factor());
                p.put("points", Math.round(penalty.points() * 10) / 10.0);
            });
            writeAdvisories(node.putArray("advisories"), score.advisories());
        }

        ArrayNode alerts = root.putArray("alerts");
        for (Alert alert : plan.alerts()) {
            ObjectNode node = alerts.addObject();
            node.put("event", alert.event());
            node.put("severity", alert.severity());
            node.put("headline", alert.headline());
            node.put("serious", alert.isSerious());
        }

        ArrayNode notes = root.putArray("coverageNotes");
        plan.coverageNotes().forEach(notes::add);

        var telemetry = plan.telemetry();
        ObjectNode t = root.putObject("telemetry");
        t.put("totalMillis", telemetry.totalMillis());
        t.put("protocols", telemetry.protocolSummary());
        t.put("weatherProtocol", telemetry.weatherProtocol());
        t.put("airQualityProtocol", telemetry.airQualityProtocol());
        t.put("alertsProtocol", telemetry.alertsProtocol());
        t.put("alertsAvailable", telemetry.alertsAvailable());
        t.put("sources", telemetry.alertsAvailable() ? 3 : 2);

        return MAPPER.writeValueAsString(root);
    }

    private static void writeAdvisories(ArrayNode target, java.util.List<Advisory> advisories) {
        for (Advisory advisory : advisories) {
            ObjectNode node = target.addObject();
            node.put("label", Advisory.label(advisory));
            node.put("text", Advisory.humanize(advisory));
            node.put("severity", advisory.severity());
            node.put("unknown", advisory instanceof Advisory.Unknown);
        }
    }

    /** Absent stays absent on the wire, so the UI can render "no data" rather than 0. */
    private static void putNullable(ObjectNode node, String field, Double value) {
        if (value == null) node.putNull(field);
        else node.put(field, Math.round(value * 10) / 10.0);
    }
}
