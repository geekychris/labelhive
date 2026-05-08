package com.datalabel.service;

import com.datalabel.template.types.FreeTextTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FreeTextTemplateTest {
    private final ObjectMapper m = new ObjectMapper();
    private final FreeTextTemplate t = new FreeTextTemplate();

    private JsonNode j(String s) { try { return m.readTree(s); } catch (Exception e) { throw new RuntimeException(e); } }

    @Test void requiresTextField() {
        assertThatThrownBy(() -> t.validateAnswer(j("{}"), j("{}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void enforcesMinLength() {
        assertThatThrownBy(() -> t.validateAnswer(j("{\"minLength\":5}"), j("{\"text\":\"hi\"}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void enforcesMaxLength() {
        assertThatThrownBy(() -> t.validateAnswer(j("{\"maxLength\":3}"), j("{\"text\":\"hello\"}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void normalizesByDefaultForEquality() {
        var cfg = j("{}");
        assertThat(t.answersEqual(cfg, j("{\"text\":\"  Hello  \"}"), j("{\"text\":\"hello\"}"))).isTrue();
    }

    @Test void respectsNormalizeFalse() {
        var cfg = j("{\"normalize\":false}");
        assertThat(t.answersEqual(cfg, j("{\"text\":\"Hello\"}"), j("{\"text\":\"hello\"}"))).isFalse();
    }

    @Test void aggregatePicksMostFrequentRaw() {
        var cfg = j("{}");
        JsonNode agg = t.aggregate(cfg, List.of(
                j("{\"text\":\"Yes\"}"),
                j("{\"text\":\"yes\"}"),
                j("{\"text\":\"No\"}")
        ));
        // Both "Yes" and "yes" normalize to "yes" and win 2 vs 1; raw retains casing of first seen.
        assertThat(agg.get("text").asText().toLowerCase()).isEqualTo("yes");
    }

    @Test void aggregateOnEmptyReturnsNull() {
        assertThat(t.aggregate(j("{}"), List.of())).isNull();
    }
}
