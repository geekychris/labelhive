package com.datalabel.service;

import com.datalabel.template.types.BoundingBoxTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class BoundingBoxTemplateTest {
    private final ObjectMapper m = new ObjectMapper();
    private final BoundingBoxTemplate t = new BoundingBoxTemplate();

    private JsonNode j(String s) { try { return m.readTree(s); } catch (Exception e) { throw new RuntimeException(e); } }

    @Test void rejectsBoxWithoutDimensions() {
        var cfg = j("{\"labels\":[\"car\"]}");
        assertThatThrownBy(() -> t.validateAnswer(cfg, j("{\"boxes\":[{\"x\":0,\"y\":0,\"w\":0,\"h\":10,\"label\":\"car\"}]}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsUnknownLabel() {
        var cfg = j("{\"labels\":[\"car\"]}");
        assertThatThrownBy(() -> t.validateAnswer(cfg, j("{\"boxes\":[{\"x\":0,\"y\":0,\"w\":1,\"h\":1,\"label\":\"plane\"}]}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void identicalBoxesGiveIou1() {
        var cfg = j("{\"labels\":[\"car\"]}");
        var a = j("{\"boxes\":[{\"x\":0,\"y\":0,\"w\":10,\"h\":10,\"label\":\"car\"}]}");
        assertThat(t.agreementScore(cfg, a, a)).isEqualTo(1.0);
    }

    @Test void halfOverlapGivesExpectedIou() {
        var cfg = j("{\"labels\":[\"car\"]}");
        var a = j("{\"boxes\":[{\"x\":0,\"y\":0,\"w\":10,\"h\":10,\"label\":\"car\"}]}");
        var b = j("{\"boxes\":[{\"x\":5,\"y\":0,\"w\":10,\"h\":10,\"label\":\"car\"}]}");
        // intersection 5x10=50, union 10x10+10x10-50=150 → 0.333
        assertThat(t.agreementScore(cfg, a, b)).isCloseTo(0.333, within(0.01));
    }

    @Test void differentLabelsDoNotMatch() {
        var cfg = j("{\"labels\":[\"car\",\"truck\"]}");
        var a = j("{\"boxes\":[{\"x\":0,\"y\":0,\"w\":10,\"h\":10,\"label\":\"car\"}]}");
        var b = j("{\"boxes\":[{\"x\":0,\"y\":0,\"w\":10,\"h\":10,\"label\":\"truck\"}]}");
        assertThat(t.agreementScore(cfg, a, b)).isEqualTo(0.0);
    }

    @Test void emptyBothSidesIsPerfect() {
        var cfg = j("{\"labels\":[\"car\"]}");
        assertThat(t.agreementScore(cfg, j("{\"boxes\":[]}"), j("{\"boxes\":[]}"))).isEqualTo(1.0);
    }
}
