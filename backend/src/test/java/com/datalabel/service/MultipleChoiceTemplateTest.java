package com.datalabel.service;

import com.datalabel.template.types.MultipleChoiceTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultipleChoiceTemplateTest {

    private final ObjectMapper m = new ObjectMapper();
    private final MultipleChoiceTemplate t = new MultipleChoiceTemplate();

    private JsonNode j(String s) { try { return m.readTree(s); } catch (Exception e) { throw new RuntimeException(e); } }

    @Test void rejectsEmptyChoices() {
        assertThatThrownBy(() -> t.validateConfig(j("{\"choices\":[]}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void singleChoiceEqualityAndAggregation() {
        var cfg = j("{\"choices\":[\"a\",\"b\",\"c\"]}");
        assertThat(t.answersEqual(cfg, j("{\"choice\":\"a\"}"), j("{\"choice\":\"a\"}"))).isTrue();
        assertThat(t.answersEqual(cfg, j("{\"choice\":\"a\"}"), j("{\"choice\":\"b\"}"))).isFalse();

        JsonNode agg = t.aggregate(cfg, List.of(
                j("{\"choice\":\"a\"}"),
                j("{\"choice\":\"a\"}"),
                j("{\"choice\":\"b\"}")
        ));
        assertThat(agg.get("choice").asText()).isEqualTo("a");
    }

    @Test void rejectsAnswerNotInChoices() {
        var cfg = j("{\"choices\":[\"a\",\"b\"]}");
        assertThatThrownBy(() -> t.validateAnswer(cfg, j("{\"choice\":\"x\"}")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
