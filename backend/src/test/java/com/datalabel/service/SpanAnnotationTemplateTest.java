package com.datalabel.service;

import com.datalabel.template.types.SpanAnnotationTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SpanAnnotationTemplateTest {

    private final ObjectMapper m = new ObjectMapper();
    private final SpanAnnotationTemplate t = new SpanAnnotationTemplate();

    private JsonNode j(String s) { try { return m.readTree(s); } catch (Exception e) { throw new RuntimeException(e); } }

    @Test void f1AgreementAndAggregation() {
        var cfg = j("{\"labels\":[\"KEYWORD\"]}");
        var a = j("{\"spans\":[{\"start\":0,\"end\":5,\"label\":\"KEYWORD\"},{\"start\":10,\"end\":14,\"label\":\"KEYWORD\"}]}");
        var b = j("{\"spans\":[{\"start\":0,\"end\":5,\"label\":\"KEYWORD\"}]}");

        // 1 of 2 vs 1 of 1 → P=0.5, R=1.0, F1=0.667
        assertThat(t.agreementScore(cfg, a, b)).isCloseTo(0.6667, within(0.001));

        // Aggregate: only span present in strict majority survives.
        var c = j("{\"spans\":[{\"start\":0,\"end\":5,\"label\":\"KEYWORD\"}]}");
        JsonNode agg = t.aggregate(cfg, List.of(a, b, c));
        assertThat(agg.get("spans")).hasSize(1);
        assertThat(agg.get("spans").get(0).get("start").asInt()).isZero();
    }
}
