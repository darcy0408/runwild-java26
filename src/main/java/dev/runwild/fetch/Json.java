package dev.runwild.fetch;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Null-safe accessors for the upstream APIs' parallel-array JSON.
 *
 * <p>Open-Meteo returns {@code {"hourly": {"time": [...], "temperature_2m": [...]}}} —
 * arrays indexed in lockstep, where an individual slot may be {@code null} when the model
 * has no value. Every read goes through here so that "absent" stays distinguishable from
 * "zero" all the way into the scoring engine.
 */
final class Json {

    private Json() {}

    /** {@code null} when the array is missing, the index is out of range, or the slot is null. */
    static Double dbl(JsonNode array, int index) {
        JsonNode node = at(array, index);
        return node == null || !node.isNumber() ? null : node.asDouble();
    }

    static Integer integer(JsonNode array, int index) {
        JsonNode node = at(array, index);
        return node == null || !node.isNumber() ? null : node.asInt();
    }

    static String text(JsonNode array, int index) {
        JsonNode node = at(array, index);
        return node == null || !node.isTextual() ? null : node.asText();
    }

    private static JsonNode at(JsonNode array, int index) {
        if (array == null || !array.isArray() || index < 0 || index >= array.size()) return null;
        JsonNode node = array.get(index);
        return node == null || node.isNull() ? null : node;
    }

    /** Open-Meteo local timestamps: "2026-08-02T14:00". */
    static LocalDateTime localDateTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * NWS timestamps carry a UTC offset: "2026-08-02T13:00:00-06:00". The offset is the
     * alert area's own, so dropping to local time lines these up with Open-Meteo's
     * {@code timezone=auto} hours without a timezone database lookup.
     */
    static LocalDateTime offsetToLocal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return localDateTime(raw);
        }
    }
}
