package com.datalabel.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * <h2>Payload schema — describes the unit JSON shape</h2>
 *
 * Built-ins fill this in with a description, required-fields list, and a
 * concrete example payload + gold answer. The UI uses it as the "format help"
 * panel on dataset-upload screens; the {@code /api/templates/{type}} endpoint
 * exposes it so external SDK users can program against it.
 */
public final class PayloadSchema {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectNode root = JsonNodeFactory.instance.objectNode();
    private final ArrayNode required = JsonNodeFactory.instance.arrayNode();

    private PayloadSchema() { root.set("requiredFields", required); }

    public static PayloadSchema builder() { return new PayloadSchema(); }

    public static JsonNode empty() {
        return PayloadSchema.builder()
                .description("This type has no specific payload requirements.")
                .build();
    }

    public PayloadSchema description(String text) {
        root.put("description", text);
        return this;
    }

    public PayloadSchema requires(String... fields) {
        for (String f : fields) required.add(f);
        return this;
    }

    public PayloadSchema example(String payloadJson, String answerJson) {
        try {
            JsonNode payload = MAPPER.readTree(payloadJson);
            JsonNode answer  = MAPPER.readTree(answerJson);
            root.set("examplePayload", payload);
            root.set("exampleAnswer",  answer);
            ObjectNode unit = JsonNodeFactory.instance.objectNode();
            unit.set("payload", payload);
            unit.set("goldAnswer", answer);
            unit.put("externalId", "example-1");
            unit.put("goldExplanation", "(optional) why this is the right answer");
            root.set("exampleUnit", unit);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid example JSON: " + e.getMessage(), e);
        }
        return this;
    }

    public ObjectNode build() { return root; }
}
