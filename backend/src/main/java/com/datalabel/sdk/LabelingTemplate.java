package com.datalabel.sdk;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * The single seam new task types implement. A {@code LabelingTemplate} owns four
 * questions about its data shape:
 *
 * <ol>
 *   <li><b>What does a job's config look like?</b>
 *       Validated by {@link #validateConfig(JsonNode)} when the admin creates the job.</li>
 *   <li><b>What does an answer look like?</b>
 *       Validated by {@link #validateAnswer(JsonNode, JsonNode)} on every submission.</li>
 *   <li><b>When are two answers considered the same?</b>
 *       {@link #answersEqual(JsonNode, JsonNode, JsonNode)} for gold-correctness;
 *       {@link #agreementScore(JsonNode, JsonNode, JsonNode)} for partial-credit
 *       inter-rater agreement (ordinal distance, span F1, IOU, etc.).</li>
 *   <li><b>How do you turn N answers into a consensus?</b>
 *       {@link #aggregate(JsonNode, List)} is what the gold-promotion path calls
 *       when high agreement licenses turning a unit into gold.</li>
 * </ol>
 *
 * <h3>Authoring a new type</h3>
 *
 * <p>1. Implement this interface, mark it {@code @Component}. Return your type id
 * from {@link #type()} (a free-form uppercase string identifies it; you don't need
 * to modify {@link TemplateType}).</p>
 *
 * <p>2. Drop a renderer in the frontend at {@code frontend/src/sdk/renderers/} and
 * register it via {@code defineRenderer(...)}. It receives the matching config +
 * payload and emits answers via {@code onChange}.</p>
 *
 * <p>3. (Optional) Add a tutorial-style markdown doc under {@code docs/templates/}
 * mirroring the docstring on this class.</p>
 *
 * <p>That's the whole extension surface. The registry, the worker feed, gold
 * mixing, trust, agreement reports, MCP exposure, and the admin UI all key off
 * the type id you choose.</p>
 *
 * @see com.datalabel.sdk
 */
public interface LabelingTemplate {

    /** Stable identifier for this template type — used as the {@code Job.templateType} value. */
    String type();

    /**
     * Throws {@link IllegalArgumentException} if a job's {@code templateConfig} is bad.
     * This runs once at job creation; if it passes, every unit's {@code goldAnswer}
     * and worker submission will be validated against the same config.
     */
    void validateConfig(JsonNode config);

    /**
     * Throws {@link IllegalArgumentException} if {@code answer} doesn't match the schema
     * declared by {@code config}.
     */
    void validateAnswer(JsonNode config, JsonNode answer);

    /**
     * Strict equality used by gold-correctness and as the default for inter-rater
     * agreement on nominal types.
     */
    boolean answersEqual(JsonNode config, JsonNode a, JsonNode b);

    /**
     * 0.0 - 1.0 partial-credit agreement between two answers (1.0 = identical).
     * Lets ordinal/structural types express something more useful than "match or not"
     * — e.g. ordinal distance for graded relevance, token-level F1 for spans, IOU
     * for boxes. Default delegates to {@link #answersEqual} so nominal types don't
     * need to override.
     */
    default double agreementScore(JsonNode config, JsonNode a, JsonNode b) {
        return answersEqual(config, a, b) ? 1.0 : 0.0;
    }

    /**
     * Consensus answer from a list of judgments. Called by the gold-promotion
     * pipeline when worker agreement is high enough; may return {@code null} if
     * no consensus is meaningful (e.g. tie or pathological input).
     */
    JsonNode aggregate(JsonNode config, List<JsonNode> answers);

    /**
     * Declarative description of this type's config knobs — the job builder UI
     * reads it and renders a form so admins can configure variants of the type
     * (e.g. "7-point search relevance scale" vs "4-point") without touching
     * Java. Default: empty (everything is optional). See {@link VariantSchema}
     * for the helpers and field-kind cheat sheet.
     */
    default JsonNode variantSchema() {
        return VariantSchema.fields().build();
    }

    /**
     * Declarative description of the per-unit JSON shape this template expects.
     * The dataset-curation UI shows this to admins so they know how to format
     * their data. Returns an object with:
     * <ul>
     *   <li>{@code description} — short prose</li>
     *   <li>{@code requiredFields} — list of payload keys that must be present</li>
     *   <li>{@code examplePayload} — a sample {@code payload} object</li>
     *   <li>{@code exampleAnswer} — a sample {@code goldAnswer} for that payload</li>
     *   <li>{@code exampleUnit} — full unit object combining the two</li>
     * </ul>
     * Plugin authors can use {@link PayloadSchema} as a builder.
     */
    default JsonNode payloadSchema() {
        return PayloadSchema.empty();
    }
}
