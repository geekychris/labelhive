package com.datalabel.template.types;

import com.datalabel.sdk.LabelingTemplate;
import com.datalabel.sdk.PayloadSchema;
import com.datalabel.sdk.TemplateType;
import com.datalabel.sdk.VariantSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * <h2>Tutorial: Free-text answer</h2>
 *
 * Open-ended response — used for transcription, paraphrase, summary, named-entity
 * extraction with free-form spans (use {@link SpanAnnotationTemplate} if you can
 * tag spans inside the source text instead).
 *
 * <h3>1. Goal</h3>
 * The worker types a string. Use sparingly: free text inherently produces low
 * inter-rater agreement, so consensus and gold scoring become noisy.
 *
 * <h3>2. Config schema</h3>
 * <pre>{@code
 * {
 *   "minLength": 1,        // optional
 *   "maxLength": 1000,     // optional
 *   "normalize": true      // optional, default true: trim + lowercase before comparison
 * }
 * }</pre>
 *
 * <h3>3. Answer schema</h3>
 * <pre>{@code
 * { "text": "the worker's response" }
 * }</pre>
 *
 * <h3>4. Equality</h3>
 * Normalized exact match: by default both sides are trimmed and lowercased
 * before comparison. Set {@code normalize: false} for case- and whitespace-
 * sensitive matching.
 *
 * <h3>5. Agreement metric</h3>
 * Default 0/1. If you want fuzzy text agreement (Levenshtein, BLEU, BERTScore)
 * write a new template — this one is intentionally cheap.
 *
 * <h3>6. Aggregation</h3>
 * Most-frequent normalized response wins (raw casing of the first occurrence is
 * preserved in the output).
 *
 * <h3>7. Common pitfalls</h3>
 * <ul>
 *   <li>Freeform text is the wrong choice when answers come from a known set —
 *       use multiple choice.</li>
 *   <li>Gold scoring on free text is brittle: prefer structural templates when
 *       you need tight quality control.</li>
 * </ul>
 */
@Component
public class FreeTextTemplate implements LabelingTemplate {
    @Override public String type() { return TemplateType.FREE_TEXT; }

    @Override public JsonNode variantSchema() {
        return VariantSchema.fields()
                .number("minLength", "Minimum length", 0, "Reject answers shorter than this.")
                .number("maxLength", "Maximum length", 1000, null)
                .bool("normalize", "Trim + lowercase before comparison", true)
                .build();
    }

    @Override public JsonNode payloadSchema() {
        return PayloadSchema.builder()
                .description("Free-form text answer. Payload typically carries a prompt or excerpt for the worker to respond to.")
                .requires("text")
                .example(
                        "{\"text\": \"Summarize this paragraph in one sentence: ...\"}",
                        "{\"text\": \"The summary goes here.\"}")
                .build();
    }

    @Override public void validateConfig(JsonNode c) { /* all fields optional */ }

    @Override public void validateAnswer(JsonNode config, JsonNode answer) {
        if (answer == null || !answer.has("text"))
            throw new IllegalArgumentException("free_text answer requires 'text'");
        String t = answer.get("text").asText("");
        int min = config == null ? 0 : config.path("minLength").asInt(0);
        int max = config == null ? Integer.MAX_VALUE : config.path("maxLength").asInt(Integer.MAX_VALUE);
        if (t.length() < min) throw new IllegalArgumentException("text too short");
        if (t.length() > max) throw new IllegalArgumentException("text too long");
    }

    @Override public boolean answersEqual(JsonNode config, JsonNode a, JsonNode b) {
        return norm(config, a).equals(norm(config, b));
    }

    @Override public JsonNode aggregate(JsonNode config, List<JsonNode> answers) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> rawByKey = new HashMap<>();
        for (JsonNode a : answers) {
            String key = norm(config, a);
            counts.merge(key, 1, Integer::sum);
            rawByKey.putIfAbsent(key, a.path("text").asText(""));
        }
        var top = counts.entrySet().stream().max(Map.Entry.comparingByValue());
        if (top.isEmpty()) return null;
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        out.put("text", rawByKey.get(top.get().getKey()));
        return out;
    }

    private static String norm(JsonNode config, JsonNode a) {
        String t = a == null ? "" : a.path("text").asText("");
        boolean normalize = config == null || config.path("normalize").asBoolean(true);
        return normalize ? t.trim().toLowerCase(Locale.ROOT) : t;
    }
}
