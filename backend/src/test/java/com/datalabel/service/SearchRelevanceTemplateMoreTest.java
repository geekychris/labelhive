package com.datalabel.service;

import com.datalabel.template.types.SearchRelevanceTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchRelevanceTemplateMoreTest {
    private final ObjectMapper m = new ObjectMapper();
    private final SearchRelevanceTemplate t = new SearchRelevanceTemplate();
    private JsonNode j(String s) { try { return m.readTree(s); } catch (Exception e) { throw new RuntimeException(e); } }

    @Test void rejectsConfigMissingScale() {
        assertThatThrownBy(() -> t.validateConfig(j("{}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsGradeOutsideScale() {
        var cfg = j("{\"scale\":[{\"label\":\"a\",\"grade\":0},{\"label\":\"b\",\"grade\":1}]}");
        assertThatThrownBy(() -> t.validateAnswer(cfg, j("{\"grade\":99}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void aggregateReturnsMedianGrade() {
        var cfg = j("{\"scale\":[{\"label\":\"a\",\"grade\":0},{\"label\":\"b\",\"grade\":1},{\"label\":\"c\",\"grade\":2},{\"label\":\"d\",\"grade\":3}]}");
        JsonNode agg = t.aggregate(cfg, List.of(
                j("{\"grade\":0}"), j("{\"grade\":2}"), j("{\"grade\":3}")));
        assertThat(agg.get("grade").asInt()).isEqualTo(2);
    }

    @Test void answersEqualMatchesExactGrade() {
        var cfg = j("{\"scale\":[{\"label\":\"a\",\"grade\":0},{\"label\":\"b\",\"grade\":1}]}");
        assertThat(t.answersEqual(cfg, j("{\"grade\":1}"), j("{\"grade\":1}"))).isTrue();
        assertThat(t.answersEqual(cfg, j("{\"grade\":0}"), j("{\"grade\":1}"))).isFalse();
    }
}
