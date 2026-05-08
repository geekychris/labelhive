/**
 * <h1>Data Label SDK — adding new annotation types</h1>
 *
 * This package is the public extension surface. Plugin authors implement
 * {@link com.datalabel.sdk.LabelingTemplate} and ship a matching React renderer
 * — that's the entire authoring contract.
 *
 * <h2>The 30-second tour</h2>
 *
 * A labeling task is described by three pieces of data:
 * <ul>
 *   <li><b>config</b> — the job-level schema (e.g. {@code {choices:[...]}} for
 *       multi-choice). One per job. Validated once at creation.</li>
 *   <li><b>payload</b> — the per-unit content (e.g. the text to classify, the
 *       image URL to inspect). Stored as JSONB.</li>
 *   <li><b>answer</b> — the worker's output (e.g. {@code {choice:"yes"}}).
 *       Validated against {@code config} at submit time.</li>
 * </ul>
 *
 * Your {@link com.datalabel.sdk.LabelingTemplate} implementation owns the
 * validation, the equality check (used for gold scoring), the partial-credit
 * agreement metric, and the consensus aggregator.
 *
 * <h2>Hello world: a "two-button thumbs" task type</h2>
 *
 * <pre>{@code
 * @Component
 * public class ThumbsTemplate implements LabelingTemplate {
 *     @Override public String type() { return "THUMBS"; }
 *
 *     @Override public void validateConfig(JsonNode c) {
 *         // No config needed — answer is just up/down.
 *     }
 *
 *     @Override public void validateAnswer(JsonNode config, JsonNode answer) {
 *         String v = answer.path("vote").asText("");
 *         if (!"up".equals(v) && !"down".equals(v))
 *             throw new IllegalArgumentException("vote must be up|down");
 *     }
 *
 *     @Override public boolean answersEqual(JsonNode c, JsonNode a, JsonNode b) {
 *         return a.path("vote").asText().equals(b.path("vote").asText());
 *     }
 *
 *     @Override public JsonNode aggregate(JsonNode c, List<JsonNode> answers) {
 *         long ups = answers.stream().filter(a -> "up".equals(a.path("vote").asText())).count();
 *         String winner = ups * 2 > answers.size() ? "up" : "down";
 *         return JsonNodeFactory.instance.objectNode().put("vote", winner);
 *     }
 * }
 * }</pre>
 *
 * Drop the file in any package — Spring's component scan will register it. Then
 * add a renderer to {@code frontend/src/sdk/renderers/} and call
 * {@code defineRenderer({type: "THUMBS", render: ThumbsRenderer})}. Done.
 *
 * <h2>Tutorial reading order</h2>
 *
 * For deeper examples, read the built-in types in order of complexity:
 * <ol>
 *   <li>{@link com.datalabel.template.types.MultipleChoiceTemplate} — the simplest case;
 *       single- vs multi-select; majority aggregation.</li>
 *   <li>{@link com.datalabel.template.types.FreeTextTemplate} — normalization-aware equality.</li>
 *   <li>{@link com.datalabel.template.types.ImageClassificationTemplate} — composition: reuses
 *       multi-choice for the answer side, only adds a payload field.</li>
 *   <li>{@link com.datalabel.template.types.SearchRelevanceTemplate} — ordinal partial credit;
 *       per-query NDCG is computed downstream by the agreement service.</li>
 *   <li>{@link com.datalabel.template.types.SpanAnnotationTemplate} — structural answers; F1 over
 *       (start,end,label) tuples; majority-vote aggregation per span.</li>
 *   <li>{@link com.datalabel.template.types.BoundingBoxTemplate} — IOU-based agreement; harder
 *       aggregation (median-by-count is a placeholder for a real NMS pass).</li>
 * </ol>
 *
 * <h2>What you don't have to do</h2>
 *
 * <ul>
 *   <li>Worker feed mixing gold + unlabeled — already wired off the type id.</li>
 *   <li>Trust scoring against gold — uses your {@link
 *       com.datalabel.sdk.LabelingTemplate#answersEqual}.</li>
 *   <li>Auto-promotion of high-agreement labels into gold — uses your
 *       {@link com.datalabel.sdk.LabelingTemplate#agreementScore} +
 *       {@link com.datalabel.sdk.LabelingTemplate#aggregate}.</li>
 *   <li>MCP server exposure — your type is reachable as a tool by name.</li>
 *   <li>Admin monitoring — completeness, leaderboards, quarantine UI all come for free.</li>
 * </ul>
 *
 * @see com.datalabel.sdk.LabelingTemplate
 * @see com.datalabel.sdk.TemplateRegistry
 * @see com.datalabel.sdk.TemplateType
 */
package com.datalabel.sdk;
