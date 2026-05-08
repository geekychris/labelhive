package com.datalabel.controller;

import com.datalabel.sdk.LabelingTemplate;
import com.datalabel.sdk.TemplateRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public catalog of registered template types — used by the job builder UI to
 * render variant config forms without hard-coding type names.
 */
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateRegistry registry;

    public TemplateController(TemplateRegistry registry) { this.registry = registry; }

    @GetMapping
    public List<Map<String, Object>> list() {
        return registry.registeredTypes().stream().sorted()
                .map(t -> Map.<String, Object>of("type", t))
                .toList();
    }

    @GetMapping("/{type}")
    public Map<String, Object> get(@PathVariable String type) {
        LabelingTemplate t = registry.get(type);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", t.type());
        out.put("variantSchema", t.variantSchema());
        out.put("payloadSchema", t.payloadSchema());
        return out;
    }

    /**
     * Dry-run a dataset against a template's validators. Body shape:
     * <pre>{
     *   "config":  { ... },         // template config (same as Job.templateConfig)
     *   "units":   [ ... ]          // list of {payload, goldAnswer?, externalId?}
     * }</pre>
     * Returns per-row errors so admins can fix before uploading. Useful in
     * SDK clients to gate `upload_units(...)` on validity.
     */
    @PostMapping("/{type}/validate-dataset")
    public Map<String, Object> validateDataset(@PathVariable String type, @RequestBody JsonNode body) {
        LabelingTemplate t = registry.get(type);

        JsonNode config = body.has("config") ? body.get("config") : null;
        if (config == null) throw new IllegalArgumentException("'config' is required");
        try { t.validateConfig(config); }
        catch (IllegalArgumentException e) {
            return Map.of("ok", false, "errors", List.of(Map.of("at", "config", "error", e.getMessage())));
        }

        JsonNode unitsNode = body.has("units") ? body.get("units") : null;
        if (unitsNode == null || !unitsNode.isArray())
            throw new IllegalArgumentException("'units' must be a JSON array");
        ArrayNode units = (ArrayNode) unitsNode;

        List<Map<String, Object>> errors = new ArrayList<>();
        int valid = 0, withGold = 0;
        for (int i = 0; i < units.size(); i++) {
            JsonNode u = units.get(i);
            JsonNode payload = u.has("payload") ? u.get("payload") : u;
            JsonNode gold    = u.has("goldAnswer") && !u.get("goldAnswer").isNull() ? u.get("goldAnswer") : null;
            try {
                if (payload == null || payload.isNull())
                    throw new IllegalArgumentException("missing payload");
                if (gold != null) { t.validateAnswer(config, gold); withGold++; }
                valid++;
            } catch (IllegalArgumentException e) {
                errors.add(Map.of("index", i, "error", e.getMessage()));
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", units.size());
        summary.put("valid", valid);
        summary.put("invalid", errors.size());
        summary.put("withGold", withGold);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", errors.isEmpty());
        out.put("summary", summary);
        out.put("errors", errors);
        return out;
    }
}
