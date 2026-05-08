package com.datalabel.service;

import com.datalabel.template.types.SpanAnnotationTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpanAnnotationTemplateMoreTest {
    private final ObjectMapper m = new ObjectMapper();
    private final SpanAnnotationTemplate t = new SpanAnnotationTemplate();
    private JsonNode j(String s) { try { return m.readTree(s); } catch (Exception e) { throw new RuntimeException(e); } }

    @Test void rejectsInvertedSpan() {
        var cfg = j("{\"labels\":[\"K\"]}");
        assertThatThrownBy(() -> t.validateAnswer(cfg, j("{\"spans\":[{\"start\":5,\"end\":2,\"label\":\"K\"}]}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsUnknownLabel() {
        var cfg = j("{\"labels\":[\"K\"]}");
        assertThatThrownBy(() -> t.validateAnswer(cfg, j("{\"spans\":[{\"start\":0,\"end\":2,\"label\":\"X\"}]}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void emptyVsEmptyIsPerfect() {
        var cfg = j("{\"labels\":[\"K\"]}");
        assertThat(t.agreementScore(cfg, j("{\"spans\":[]}"), j("{\"spans\":[]}"))).isEqualTo(1.0);
    }

    @Test void emptyVsNonEmptyIsZero() {
        var cfg = j("{\"labels\":[\"K\"]}");
        assertThat(t.agreementScore(cfg,
                j("{\"spans\":[]}"),
                j("{\"spans\":[{\"start\":0,\"end\":1,\"label\":\"K\"}]}"))).isEqualTo(0.0);
    }

    @Test void identicalSetsAreEqual() {
        var cfg = j("{\"labels\":[\"K\"]}");
        var a = j("{\"spans\":[{\"start\":0,\"end\":2,\"label\":\"K\"},{\"start\":5,\"end\":8,\"label\":\"K\"}]}");
        var b = j("{\"spans\":[{\"start\":5,\"end\":8,\"label\":\"K\"},{\"start\":0,\"end\":2,\"label\":\"K\"}]}");
        assertThat(t.answersEqual(cfg, a, b)).isTrue();
    }
}
