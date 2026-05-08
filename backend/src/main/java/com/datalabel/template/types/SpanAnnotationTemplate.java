package com.datalabel.template.types;

import com.datalabel.sdk.LabelingTemplate;
import com.datalabel.sdk.PayloadSchema;
import com.datalabel.sdk.TemplateType;
import com.datalabel.sdk.VariantSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * <h2>Tutorial: Span annotation (NER, keyword tagging)</h2>
 *
 * The worker highlights character ranges inside a piece of text and tags each
 * span with a label. This is the classic NER setup, and it's also how you do
 * keyword extraction, PII detection, claim/evidence marking, etc.
 *
 * <h3>1. Goal</h3>
 * Get structured spans out of unstructured text, where each span has a
 * (start, end, label) tuple.
 *
 * <h3>2. Config schema</h3>
 * <pre>{@code
 * {
 *   "labels":    ["PRODUCT", "MODEL", "COMPANY"],   // required, non-empty
 *   "textField": "text"                             // optional, default "text"
 * }
 * }</pre>
 *
 * <h3>3. Payload shape</h3>
 * <pre>{@code
 * { "text": "Anthropic released Claude 4.7 last week." }
 * }</pre>
 *
 * <h3>4. Answer schema</h3>
 * <pre>{@code
 * {
 *   "spans": [
 *     { "start": 0,  "end": 9,  "label": "COMPANY" },
 *     { "start": 19, "end": 29, "label": "MODEL"   }
 *   ]
 * }
 * }</pre>
 *
 * <p>Offsets are character offsets into the source text (not token offsets).
 * Inverted spans ({@code end <= start}) are rejected at validation time.</p>
 *
 * <h3>5. Equality</h3>
 * Set equality of (start, end, label) tuples. Order doesn't matter.
 *
 * <h3>6. Agreement metric</h3>
 * F1 of exact (start, end, label) tuples. Two annotators agree fully iff they
 * marked the same spans with the same labels. F1 lets us reward partial matches
 * (e.g. one worker tagged 4 spans, another tagged 3 of the same — F1 = 0.857).
 *
 * <h3>7. Aggregation</h3>
 * Strict majority per span. A span survives the consensus only if more than
 * half of judges marked exactly that (start, end, label).
 *
 * <h3>8. Common pitfalls</h3>
 * <ul>
 *   <li>Per-character offsets are unforgiving — the renderer should help the
 *       worker click whole tokens. The bundled span renderer does word-level
 *       tagging by default.</li>
 *   <li>If labels are nested (an entity inside a longer phrase), spans may
 *       overlap. The metric copes (it just F1's the tuple set) but the UI
 *       needs to make overlapping selections feasible.</li>
 *   <li>For span-level partial credit (substring overlap, IOU on tokens) —
 *       extend this template; the contract supports any score in [0,1].</li>
 * </ul>
 */
@Component
public class SpanAnnotationTemplate implements LabelingTemplate {

    @Override public String type() { return TemplateType.SPAN_ANNOTATION; }

    @Override public JsonNode variantSchema() {
        return VariantSchema.fields()
                .stringList("labels", "Span labels", true, "e.g. PERSON, ORG, KEYWORD.")
                .string("textField", "Payload field with text", "text", null)
                .build();
    }

    @Override public JsonNode payloadSchema() {
        return PayloadSchema.builder()
                .description("Payload carries the text to annotate under the configured textField (default 'text'). Span offsets are character offsets into that text.")
                .requires("text")
                .example(
                        "{\"text\": \"Anthropic released Claude 4.7 last week.\"}",
                        "{\"spans\": [" +
                                "{\"start\": 0, \"end\": 9, \"label\": \"COMPANY\"}," +
                                "{\"start\": 19, \"end\": 29, \"label\": \"MODEL\"}]}")
                .build();
    }

    @Override public void validateConfig(JsonNode c) {
        if (c == null || !c.has("labels") || !c.get("labels").isArray() || c.get("labels").isEmpty())
            throw new IllegalArgumentException("span_annotation config requires 'labels'");
    }

    @Override public void validateAnswer(JsonNode config, JsonNode answer) {
        if (answer == null || !answer.has("spans") || !answer.get("spans").isArray())
            throw new IllegalArgumentException("span_annotation answer requires 'spans' array");
        Set<String> labels = new HashSet<>();
        config.get("labels").forEach(n -> labels.add(n.asText()));
        for (JsonNode s : answer.get("spans")) {
            if (!s.has("start") || !s.has("end") || !s.has("label"))
                throw new IllegalArgumentException("span needs start, end, label");
            if (s.get("end").asInt() <= s.get("start").asInt())
                throw new IllegalArgumentException("span end must exceed start");
            if (!labels.contains(s.get("label").asText()))
                throw new IllegalArgumentException("unknown label: " + s.get("label").asText());
        }
    }

    @Override public boolean answersEqual(JsonNode config, JsonNode a, JsonNode b) {
        return spans(a).equals(spans(b));
    }

    @Override public double agreementScore(JsonNode config, JsonNode a, JsonNode b) {
        Set<String> sa = spans(a), sb = spans(b);
        if (sa.isEmpty() && sb.isEmpty()) return 1.0;
        Set<String> inter = new HashSet<>(sa); inter.retainAll(sb);
        double precision = sa.isEmpty() ? 0 : (double) inter.size() / sa.size();
        double recall    = sb.isEmpty() ? 0 : (double) inter.size() / sb.size();
        if (precision + recall == 0) return 0.0;
        return 2 * precision * recall / (precision + recall);
    }

    @Override public JsonNode aggregate(JsonNode config, List<JsonNode> answers) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, JsonNode> byKey = new HashMap<>();
        int n = answers.size();
        for (JsonNode a : answers) for (JsonNode s : a.get("spans")) {
            String k = key(s);
            counts.merge(k, 1, Integer::sum);
            byKey.putIfAbsent(k, s);
        }
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        ArrayNode arr = out.putArray("spans");
        counts.entrySet().stream()
                .filter(e -> e.getValue() * 2 > n)
                .map(e -> byKey.get(e.getKey()))
                .forEach(arr::add);
        return out;
    }

    private static Set<String> spans(JsonNode answer) {
        Set<String> out = new HashSet<>();
        if (answer == null || !answer.has("spans")) return out;
        for (JsonNode s : answer.get("spans")) out.add(key(s));
        return out;
    }
    private static String key(JsonNode s) {
        return s.get("start").asInt() + ":" + s.get("end").asInt() + ":" + s.get("label").asText();
    }
}
