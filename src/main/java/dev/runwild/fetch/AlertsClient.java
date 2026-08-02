package dev.runwild.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.runwild.model.Alert;

import java.util.ArrayList;
import java.util.List;

/** Parses active National Weather Service alerts (GeoJSON FeatureCollection). */
public final class AlertsClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<Alert> parse(String json) throws Exception {
        JsonNode features = MAPPER.readTree(json).path("features");
        if (!features.isArray()) return List.of();

        var alerts = new ArrayList<Alert>();
        for (JsonNode feature : features) {
            JsonNode p = feature.path("properties");
            String event = p.path("event").asText(null);
            if (event == null) continue;
            alerts.add(new Alert(
                    event,
                    p.path("severity").asText("Unknown"),
                    firstNonBlank(p.path("headline").asText(null), p.path("description").asText(null), event),
                    Json.offsetToLocal(p.path("onset").asText(null)),
                    Json.offsetToLocal(p.path("expires").asText(null))));
        }
        return List.copyOf(alerts);
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) return c.length() > 200 ? c.substring(0, 200) + "…" : c;
        }
        return "";
    }
}
