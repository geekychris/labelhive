package com.datalabel.service;

import com.datalabel.template.types.MultipleChoiceTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultipleChoiceTemplateMoreTest {
    private final ObjectMapper m = new ObjectMapper();
    private final MultipleChoiceTemplate t = new MultipleChoiceTemplate();
    private JsonNode j(String s) { try { return m.readTree(s); } catch (Exception e) { throw new RuntimeException(e); } }

    @Test void multiAnswerEqualityIsSetEquality() {
        var cfg = j("{\"choices\":[\"a\",\"b\",\"c\"],\"allowMultiple\":true}");
        assertThat(t.answersEqual(cfg,
                j("{\"choices\":[\"a\",\"b\"]}"),
                j("{\"choices\":[\"b\",\"a\"]}"))).isTrue();
        assertThat(t.answersEqual(cfg,
                j("{\"choices\":[\"a\"]}"),
                j("{\"choices\":[\"a\",\"b\"]}"))).isFalse();
    }

    @Test void multiRequiresChoicesArrayNotChoiceField() {
        var cfg = j("{\"choices\":[\"a\",\"b\"],\"allowMultiple\":true}");
        assertThatThrownBy(() -> t.validateAnswer(cfg, j("{\"choice\":\"a\"}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void multiAggregateUsesStrictMajorityPerOption() {
        var cfg = j("{\"choices\":[\"a\",\"b\",\"c\"],\"allowMultiple\":true}");
        JsonNode agg = t.aggregate(cfg, List.of(
                j("{\"choices\":[\"a\",\"b\"]}"),
                j("{\"choices\":[\"a\",\"c\"]}"),
                j("{\"choices\":[\"a\"]}")
        ));
        // 'a' appears in 3/3, 'b' in 1/3, 'c' in 1/3 → only 'a' clears strict majority.
        assertThat(agg.get("choices")).hasSize(1);
        assertThat(agg.get("choices").get(0).asText()).isEqualTo("a");
    }

    @Test void aggregateOfTieReturnsOneWinner() {
        var cfg = j("{\"choices\":[\"a\",\"b\"]}");
        JsonNode agg = t.aggregate(cfg, List.of(
                j("{\"choice\":\"a\"}"), j("{\"choice\":\"b\"}")));
        // Either is acceptable, but result must be valid.
        assertThat(agg.get("choice").asText()).isIn("a", "b");
    }

    @Test void rejectsAnswerWithMissingChoiceField() {
        var cfg = j("{\"choices\":[\"a\"]}");
        assertThatThrownBy(() -> t.validateAnswer(cfg, j("{}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsNullConfig() {
        assertThatThrownBy(() -> t.validateConfig(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
