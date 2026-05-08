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
 * <h2>Tutorial: Bounding-box annotation</h2>
 *
 * The worker draws labeled rectangles on an image — the canonical detection-
 * dataset task. This template handles the data shape and the agreement math
 * (mean best-IOU per box, restricted to same-label pairs); the actual drawing
 * UI on the frontend is left as a stub for you to extend.
 *
 * <h3>1. Goal</h3>
 * Localize objects in an image with rectangular boxes and class labels.
 *
 * <h3>2. Config schema</h3>
 * <pre>{@code
 * {
 *   "labels":     ["car", "person", "bike"],
 *   "imageField": "imageUrl"
 * }
 * }</pre>
 *
 * <h3>3. Payload shape</h3>
 * <pre>{@code
 * { "imageUrl": "https://example.test/scene.jpg" }
 * }</pre>
 *
 * <h3>4. Answer schema</h3>
 * <pre>{@code
 * {
 *   "boxes": [
 *     { "x": 10, "y": 12, "w": 50, "h": 80, "label": "car" },
 *     { "x": 90, "y":  4, "w": 40, "h": 90, "label": "person" }
 *   ]
 * }
 * }</pre>
 *
 * Coordinates are in image-pixel units (or any consistent unit you choose).
 *
 * <h3>5. Equality</h3>
 * "Equal" if {@link #agreementScore agreement score} ≥ 0.5 — bounding boxes are
 * never pixel-identical between humans, so we use IOU thresholding.
 *
 * <h3>6. Agreement metric</h3>
 * For each box in {@code A}, find its best-IOU same-label match in {@code B};
 * average over {@code max(|A|, |B|)} so missing/extra boxes are penalized.
 *
 * <h3>7. Aggregation</h3>
 * Naive: pick the answer with the median number of boxes. <em>This is a
 * placeholder</em> — a production system should do non-max suppression across
 * judgments. Treat this as the simplest thing that works for a demo.
 *
 * <h3>8. What's intentionally not built</h3>
 * The frontend renderer ({@code BoundingBoxRenderer.tsx}) is a stub. Drop in
 * something like {@code react-image-annotate} or a canvas drawing component
 * and you have a complete pipeline.
 */
@Component
public class BoundingBoxTemplate implements LabelingTemplate {

    private record Box(double x, double y, double w, double h, String label) {}

    @Override public String type() { return TemplateType.BOUNDING_BOX; }

    @Override public JsonNode variantSchema() {
        return VariantSchema.fields()
                .stringList("labels", "Box labels", true, "Class labels you want boxes drawn for.")
                .string("imageField", "Payload field with image URL", "imageUrl", null)
                .build();
    }

    @Override public JsonNode payloadSchema() {
        return PayloadSchema.builder()
                .description("Payload must include the image URL under the configured imageField (default 'imageUrl'). Box coordinates are pixel-relative.")
                .requires("imageUrl")
                .example(
                        "{\"imageUrl\": \"https://example.test/scene.jpg\"}",
                        "{\"boxes\": [{\"x\": 10, \"y\": 12, \"w\": 50, \"h\": 80, \"label\": \"car\"}]}")
                .build();
    }

    @Override public void validateConfig(JsonNode c) {
        if (c == null || !c.has("labels") || !c.get("labels").isArray() || c.get("labels").isEmpty())
            throw new IllegalArgumentException("bounding_box config requires 'labels'");
    }

    @Override public void validateAnswer(JsonNode config, JsonNode answer) {
        if (answer == null || !answer.has("boxes") || !answer.get("boxes").isArray())
            throw new IllegalArgumentException("bounding_box answer requires 'boxes'");
        Set<String> labels = new HashSet<>();
        config.get("labels").forEach(n -> labels.add(n.asText()));
        for (JsonNode b : answer.get("boxes")) {
            for (String f : List.of("x","y","w","h","label"))
                if (!b.has(f)) throw new IllegalArgumentException("box missing " + f);
            if (b.get("w").asDouble() <= 0 || b.get("h").asDouble() <= 0)
                throw new IllegalArgumentException("box w/h must be positive");
            if (!labels.contains(b.get("label").asText()))
                throw new IllegalArgumentException("unknown label");
        }
    }

    @Override public boolean answersEqual(JsonNode config, JsonNode a, JsonNode b) {
        return agreementScore(config, a, b) >= 0.5;
    }

    @Override public double agreementScore(JsonNode config, JsonNode a, JsonNode b) {
        List<Box> A = boxes(a), B = boxes(b);
        if (A.isEmpty() && B.isEmpty()) return 1.0;
        if (A.isEmpty() || B.isEmpty()) return 0.0;
        double sum = 0;
        for (Box x : A) {
            double best = 0;
            for (Box y : B) if (x.label.equals(y.label)) best = Math.max(best, iou(x, y));
            sum += best;
        }
        return sum / Math.max(A.size(), B.size());
    }

    @Override public JsonNode aggregate(JsonNode config, List<JsonNode> answers) {
        if (answers.isEmpty()) return null;
        List<JsonNode> sorted = new ArrayList<>(answers);
        sorted.sort(Comparator.comparingInt(a -> a.path("boxes").size()));
        JsonNode picked = sorted.get(sorted.size() / 2);
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        ArrayNode arr = out.putArray("boxes");
        for (JsonNode b : picked.get("boxes")) arr.add(b);
        return out;
    }

    private static List<Box> boxes(JsonNode answer) {
        List<Box> out = new ArrayList<>();
        if (answer == null || !answer.has("boxes")) return out;
        for (JsonNode b : answer.get("boxes")) {
            out.add(new Box(
                    b.get("x").asDouble(), b.get("y").asDouble(),
                    b.get("w").asDouble(), b.get("h").asDouble(),
                    b.get("label").asText()));
        }
        return out;
    }

    private static double iou(Box a, Box b) {
        double ax2 = a.x + a.w, ay2 = a.y + a.h;
        double bx2 = b.x + b.w, by2 = b.y + b.h;
        double ix1 = Math.max(a.x, b.x), iy1 = Math.max(a.y, b.y);
        double ix2 = Math.min(ax2, bx2), iy2 = Math.min(ay2, by2);
        double iw = Math.max(0, ix2 - ix1), ih = Math.max(0, iy2 - iy1);
        double inter = iw * ih;
        double union = a.w * a.h + b.w * b.h - inter;
        return union <= 0 ? 0 : inter / union;
    }
}
