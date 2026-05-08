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
 * <h2>Tutorial: Multiple-choice classification</h2>
 *
 * The simplest possible labeling task: one item, pick one (or more) from a fixed
 * list of choices. Sentiment, intent classification, content moderation buckets,
 * yes/no/maybe — all of these are multiple choice underneath.
 *
 * <h3>1. Goal</h3>
 * Show a worker a payload (typically text) and have them select one or more
 * options. Use this when there's a closed vocabulary the answer must come from.
 *
 * <h3>2. Config schema</h3>
 * <pre>{@code
 * {
 *   "choices": ["positive", "neutral", "negative"],   // required, non-empty
 *   "allowMultiple": false                            // optional, default false
 * }
 * }</pre>
 *
 * <h3>3. Answer schema</h3>
 * <pre>{@code
 * // Single-select:
 * { "choice": "positive" }
 *
 * // Multi-select (allowMultiple=true):
 * { "choices": ["positive", "mixed"] }
 * }</pre>
 *
 * <h3>4. Equality (for gold scoring)</h3>
 * Single-select: exact string equality on the {@code choice} field.<br>
 * Multi-select: set equality of the {@code choices} array (order-independent).
 *
 * <h3>5. Agreement metric</h3>
 * Inherits the default — match (1.0) or no match (0.0). For nominal categories
 * this is appropriate; if you need ordinal categories use
 * {@link SearchRelevanceTemplate} as a model.
 *
 * <h3>6. Aggregation (consensus)</h3>
 * Single-select: the choice that appears most often.<br>
 * Multi-select: per-option strict majority — an option survives only if more
 * than half of judges picked it. Avoids "everyone clicked one different thing
 * so we OR them all together" pathologies.
 *
 * <h3>7. Common pitfalls</h3>
 * <ul>
 *   <li>Don't conflate "yes / maybe / no" with an ordinal scale; if distance
 *       between answers matters, use a graded type (see
 *       {@link SearchRelevanceTemplate}).</li>
 *   <li>Long choice lists (&gt; 12) are usually a sign you should split the task.</li>
 * </ul>
 *
 * <h3>8. Example</h3>
 * Demo dataset {@code demo/sentiment_social.json} ships with this template.
 */
@Component
public class MultipleChoiceTemplate implements LabelingTemplate {
    @Override public String type() { return TemplateType.MULTIPLE_CHOICE; }

    @Override public JsonNode variantSchema() {
        return VariantSchema.fields()
                .stringList("choices", "Choice options", true,
                        "The closed set the worker picks from.")
                .bool("allowMultiple", "Allow multi-select", false)
                .build();
    }

    @Override public JsonNode payloadSchema() {
        return PayloadSchema.builder()
                .description("Payload carries the text to classify. The worker picks from the configured choices.")
                .requires("text")
                .example(
                        "{\"text\": \"Just shipped my first MCP server!\"}",
                        "{\"choice\": \"positive\"}")
                .build();
    }

    @Override public void validateConfig(JsonNode c) {
        if (c == null || !c.has("choices") || !c.get("choices").isArray() || c.get("choices").isEmpty())
            throw new IllegalArgumentException("multiple_choice config requires non-empty 'choices' array");
    }

    @Override public void validateAnswer(JsonNode config, JsonNode answer) {
        if (answer == null) throw new IllegalArgumentException("answer required");
        Set<String> valid = new HashSet<>();
        config.get("choices").forEach(n -> valid.add(n.asText()));
        boolean multi = config.path("allowMultiple").asBoolean(false);
        if (multi) {
            JsonNode arr = answer.get("choices");
            if (arr == null || !arr.isArray() || arr.isEmpty())
                throw new IllegalArgumentException("expected 'choices' array");
            for (JsonNode n : arr) if (!valid.contains(n.asText()))
                throw new IllegalArgumentException("invalid choice: " + n.asText());
        } else {
            if (!answer.has("choice")) throw new IllegalArgumentException("expected 'choice'");
            if (!valid.contains(answer.get("choice").asText()))
                throw new IllegalArgumentException("invalid choice: " + answer.get("choice").asText());
        }
    }

    @Override public boolean answersEqual(JsonNode config, JsonNode a, JsonNode b) {
        boolean multi = config.path("allowMultiple").asBoolean(false);
        if (multi) {
            return toSet(a.get("choices")).equals(toSet(b.get("choices")));
        }
        return Objects.equals(a.path("choice").asText(null), b.path("choice").asText(null));
    }

    @Override public JsonNode aggregate(JsonNode config, List<JsonNode> answers) {
        boolean multi = config.path("allowMultiple").asBoolean(false);
        if (answers.isEmpty()) return null;
        if (multi) {
            Map<String, Integer> counts = new HashMap<>();
            int n = answers.size();
            for (JsonNode a : answers) for (JsonNode c : a.get("choices"))
                counts.merge(c.asText(), 1, Integer::sum);
            ObjectNode out = JsonNodeFactory.instance.objectNode();
            var arr = out.putArray("choices");
            counts.entrySet().stream()
                    .filter(e -> e.getValue() * 2 > n) // strict majority
                    .map(Map.Entry::getKey).sorted().forEach(arr::add);
            return out;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (JsonNode a : answers) counts.merge(a.path("choice").asText(""), 1, Integer::sum);
        String top = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        if (top == null) return null;
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        out.put("choice", top);
        return out;
    }

    private static Set<String> toSet(JsonNode arr) {
        if (arr == null) return Set.of();
        Set<String> s = new HashSet<>();
        arr.forEach(n -> s.add(n.asText()));
        return s;
    }
}
