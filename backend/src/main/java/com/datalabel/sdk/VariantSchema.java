package com.datalabel.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * <h2>Variant schema — declarative per-type config form</h2>
 *
 * The platform's job builder UI is schema-driven: it asks each
 * {@link LabelingTemplate} what knobs it has, and renders a form. That's how
 * admins tweak a "search relevance with 7-point scale" or a multi-choice with
 * a different choice list, all without Java edits.
 *
 * <p>Field {@code kind}s the built-in form renderer understands:</p>
 * <ul>
 *   <li>{@code "string"} — single-line text</li>
 *   <li>{@code "number"} — integer/double</li>
 *   <li>{@code "boolean"} — checkbox</li>
 *   <li>{@code "string-list"} — chip input (array of strings)</li>
 *   <li>{@code "scale"} — array of {@code {label, grade}} pairs (for ordinal types)</li>
 *   <li>{@code "json"} — fallback raw JSON editor</li>
 * </ul>
 *
 * <p>Custom types can use any string for {@code kind} — the frontend will fall
 * back to the JSON editor if it doesn't recognize the kind.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * VariantSchema.fields()
 *     .stringList("choices", "Choice options", true, "Worker picks from this list")
 *     .bool("allowMultiple", "Allow multi-select", false)
 *     .build();
 * }</pre>
 */
public final class VariantSchema {

    private final ArrayNode fields = JsonNodeFactory.instance.arrayNode();

    private VariantSchema() {}
    public static VariantSchema fields() { return new VariantSchema(); }

    public VariantSchema string(String name, String label, String defaultValue, String help) {
        return add(name, label, "string", required(false), help, defaultValue == null ? null : asText(defaultValue), null);
    }
    public VariantSchema number(String name, String label, Number defaultValue, String help) {
        return add(name, label, "number", required(false), help, defaultValue == null ? null : asNumber(defaultValue), null);
    }
    public VariantSchema bool(String name, String label, boolean defaultValue) {
        return add(name, label, "boolean", required(false), null, JsonNodeFactory.instance.booleanNode(defaultValue), null);
    }
    public VariantSchema stringList(String name, String label, boolean required, String help) {
        return add(name, label, "string-list", required, help, null, null);
    }
    public VariantSchema scale(String name, String label, JsonNode defaultValue, String help) {
        return add(name, label, "scale", required(true), help, defaultValue, null);
    }

    private VariantSchema add(String name, String label, String kind, boolean required,
                              String help, JsonNode defaultValue, JsonNode extras) {
        ObjectNode f = JsonNodeFactory.instance.objectNode();
        f.put("name", name);
        f.put("label", label);
        f.put("kind", kind);
        if (required) f.put("required", true);
        if (help != null) f.put("help", help);
        if (defaultValue != null) f.set("default", defaultValue);
        if (extras != null) f.setAll((ObjectNode) extras);
        fields.add(f);
        return this;
    }

    public ObjectNode build() {
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        out.set("fields", fields);
        return out;
    }

    private static boolean required(boolean v) { return v; }
    private static JsonNode asText(String s) { return JsonNodeFactory.instance.textNode(s); }
    private static JsonNode asNumber(Number n) {
        if (n instanceof Integer) return JsonNodeFactory.instance.numberNode((Integer) n);
        if (n instanceof Long) return JsonNodeFactory.instance.numberNode((Long) n);
        return JsonNodeFactory.instance.numberNode(n.doubleValue());
    }
}
