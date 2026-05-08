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
 * <h2>Tutorial: Search-relevance grading (with NDCG)</h2>
 *
 * Each unit is one (query, search-result) pair. The worker grades how relevant
 * the result is on an ordinal scale (e.g. 0=irrelevant ... 3=perfect). The
 * agreement service computes per-query NDCG by joining all units that share
 * the same {@code queryId}.
 *
 * <h3>1. Goal</h3>
 * Build a labeled relevance dataset suitable for training/evaluating a search
 * ranker. Each (query, result) gets one ordinal grade.
 *
 * <h3>2. Config schema</h3>
 * <pre>{@code
 * {
 *   "scale": [
 *     { "label": "irrelevant", "grade": 0 },
 *     { "label": "related",    "grade": 1 },
 *     { "label": "relevant",   "grade": 2 },
 *     { "label": "perfect",    "grade": 3 }
 *   ],
 *   "queryField": "queryId"   // payload field that groups results into a query
 * }
 * }</pre>
 *
 * <h3>3. Payload shape</h3>
 * <pre>{@code
 * {
 *   "queryId": "q1",
 *   "query":   "best vector database for RAG in 2026",
 *   "result": {
 *     "title":   "pgvector 1.0 — Postgres-native vector search",
 *     "snippet": "...",
 *     "url":     "https://..."
 *   }
 * }
 * }</pre>
 *
 * <h3>4. Answer schema</h3>
 * <pre>{@code
 * { "grade": 2 }
 * }</pre>
 *
 * <h3>5. Equality</h3>
 * Strict integer match on {@code grade} (used for gold-correctness).
 *
 * <h3>6. Agreement metric (the interesting part)</h3>
 * Ordinal partial credit:
 * <pre>{@code 1 - |a - b| / range }</pre>
 * — adjacent grades get high credit, far-apart grades get little. This is what
 * makes worker-trust scoring sensible: a worker who grades a 3 as a 2 should
 * cost less trust than one who grades it 0.
 *
 * <h3>7. Aggregation</h3>
 * Median grade across judgments. Robust against outlier raters.
 *
 * <h3>8. Per-query NDCG</h3>
 * The {@code AgreementService} groups units by {@code queryField} (default
 * {@code queryId}), takes the median grade per result as the predicted score,
 * uses the gold grade as the ideal, and computes NDCG. Reported in the job
 * report under {@code ndcg.perQuery} and {@code ndcg.meanNdcg}.
 *
 * <h3>9. Common pitfalls</h3>
 * <ul>
 *   <li>Don't put the query and result in the same string — keep
 *       {@code payload.query} and {@code payload.result} separate so the
 *       renderer can style them.</li>
 *   <li>The grade integers must be unique within {@code scale}.</li>
 * </ul>
 */
@Component
public class SearchRelevanceTemplate implements LabelingTemplate {
    @Override public String type() { return TemplateType.SEARCH_RELEVANCE; }

    @Override public JsonNode payloadSchema() {
        return PayloadSchema.builder()
                .description("One unit per (query, result) pair. queryId groups results into the same query for NDCG scoring. The result object contains title/snippet/url for the worker to read.")
                .requires("queryId", "query", "result")
                .example(
                        "{\"queryId\": \"q1\", \"query\": \"best vector database for RAG\", " +
                                "\"result\": {\"title\": \"pgvector 1.0\", \"snippet\": \"...\", \"url\": \"https://...\"}}",
                        "{\"grade\": 3}")
                .build();
    }

    @Override public JsonNode variantSchema() {
        var defaultScale = JsonNodeFactory.instance.arrayNode();
        defaultScale.add(JsonNodeFactory.instance.objectNode().put("label", "irrelevant").put("grade", 0));
        defaultScale.add(JsonNodeFactory.instance.objectNode().put("label", "related").put("grade", 1));
        defaultScale.add(JsonNodeFactory.instance.objectNode().put("label", "relevant").put("grade", 2));
        defaultScale.add(JsonNodeFactory.instance.objectNode().put("label", "perfect").put("grade", 3));
        return VariantSchema.fields()
                .scale("scale", "Relevance scale", defaultScale,
                        "Ordinal grading scale. Add or remove rows to change granularity.")
                .string("queryField", "Query-id payload field", "queryId",
                        "The payload field that groups results into the same query for NDCG.")
                .build();
    }

    @Override public void validateConfig(JsonNode c) {
        if (c == null || !c.has("scale") || !c.get("scale").isArray() || c.get("scale").isEmpty())
            throw new IllegalArgumentException("search_relevance config requires 'scale'");
        for (JsonNode s : c.get("scale")) {
            if (!s.has("label") || !s.has("grade"))
                throw new IllegalArgumentException("each scale entry needs 'label' and 'grade'");
        }
    }

    @Override public void validateAnswer(JsonNode config, JsonNode answer) {
        if (answer == null || !answer.has("grade"))
            throw new IllegalArgumentException("search_relevance answer requires 'grade'");
        int g = answer.get("grade").asInt(Integer.MIN_VALUE);
        boolean ok = false;
        for (JsonNode s : config.get("scale")) if (s.get("grade").asInt() == g) { ok = true; break; }
        if (!ok) throw new IllegalArgumentException("grade not in scale: " + g);
    }

    @Override public boolean answersEqual(JsonNode c, JsonNode a, JsonNode b) {
        return a.path("grade").asInt() == b.path("grade").asInt();
    }

    /** Ordinal partial credit: 1 - |a-b| / range. */
    @Override public double agreementScore(JsonNode config, JsonNode a, JsonNode b) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (JsonNode s : config.get("scale")) {
            int g = s.get("grade").asInt();
            if (g < min) min = g; if (g > max) max = g;
        }
        int range = Math.max(1, max - min);
        int ag = a.path("grade").asInt(), bg = b.path("grade").asInt();
        return 1.0 - Math.min(1.0, Math.abs(ag - bg) / (double) range);
    }

    @Override public JsonNode aggregate(JsonNode config, List<JsonNode> answers) {
        if (answers.isEmpty()) return null;
        List<Integer> grades = new ArrayList<>();
        for (JsonNode a : answers) grades.add(a.path("grade").asInt());
        Collections.sort(grades);
        int median = grades.get(grades.size() / 2);
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        out.put("grade", median);
        return out;
    }
}
