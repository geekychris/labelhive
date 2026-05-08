package com.datalabel.template.types;

import com.datalabel.sdk.LabelingTemplate;
import com.datalabel.sdk.PayloadSchema;
import com.datalabel.sdk.TemplateType;
import com.datalabel.sdk.VariantSchema;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * <h2>Tutorial: Image classification (composition)</h2>
 *
 * <p>This template is a worked example of how to <em>compose</em> templates —
 * the answer/config shape is identical to {@link MultipleChoiceTemplate} so we
 * delegate every method. The only thing that changes is what the worker UI
 * does on the frontend (it pulls an image URL out of the unit's payload).</p>
 *
 * <h3>Why a separate type at all?</h3>
 * Two reasons:
 * <ol>
 *   <li>The frontend renderer registry needs a different component — the
 *       payload has an {@code imageUrl} we want to render as an {@code <img>},
 *       not stringify into a div.</li>
 *   <li>Reports / monitoring can show "image" tasks distinct from text-only
 *       multi-choice tasks.</li>
 * </ol>
 *
 * <h3>Config schema</h3>
 * <pre>{@code
 * {
 *   "imageField": "imageUrl",   // optional, the payload key holding the URL (default "imageUrl")
 *   "choices": ["cat","dog","other"],
 *   "allowMultiple": false
 * }
 * }</pre>
 *
 * <h3>Payload shape</h3>
 * <pre>{@code
 * { "imageUrl": "https://example.test/cat.jpg", "caption": "optional caption" }
 * }</pre>
 *
 * <h3>Answer schema</h3>
 * Same as {@link MultipleChoiceTemplate}: {@code {choice}} or {@code {choices: []}}.
 *
 * <h3>Lesson</h3>
 * <p>When two tasks share semantics, compose — don't copy. If you need a third
 * variant (e.g. video classification), write a new template that delegates to
 * this one and changes only the renderer.</p>
 */
@Component
public class ImageClassificationTemplate implements LabelingTemplate {

    private final MultipleChoiceTemplate delegate;

    public ImageClassificationTemplate(MultipleChoiceTemplate delegate) { this.delegate = delegate; }

    @Override public String type() { return TemplateType.IMAGE_CLASSIFICATION; }

    @Override public JsonNode variantSchema() {
        return VariantSchema.fields()
                .string("imageField", "Payload field with image URL", "imageUrl",
                        "The key in each unit's payload that holds the image URL.")
                .stringList("choices", "Choice options", true, null)
                .bool("allowMultiple", "Allow multi-select", false)
                .build();
    }

    @Override public JsonNode payloadSchema() {
        return PayloadSchema.builder()
                .description("Payload must include the image URL under the configured imageField (default 'imageUrl'). Caption is optional context for the worker.")
                .requires("imageUrl")
                .example(
                        "{\"imageUrl\": \"https://example.test/cat.jpg\", \"caption\": \"A cat in a box\"}",
                        "{\"choice\": \"cat\"}")
                .build();
    }

    @Override public void validateConfig(JsonNode c) { delegate.validateConfig(c); }
    @Override public void validateAnswer(JsonNode c, JsonNode a) { delegate.validateAnswer(c, a); }
    @Override public boolean answersEqual(JsonNode c, JsonNode a, JsonNode b) { return delegate.answersEqual(c, a, b); }
    @Override public JsonNode aggregate(JsonNode c, List<JsonNode> as) { return delegate.aggregate(c, as); }
}
