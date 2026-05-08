package com.datalabel.service;

import com.datalabel.template.types.ImageClassificationTemplate;
import com.datalabel.template.types.MultipleChoiceTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageClassificationTemplateTest {
    private final ObjectMapper m = new ObjectMapper();
    private final ImageClassificationTemplate t = new ImageClassificationTemplate(new MultipleChoiceTemplate());

    private JsonNode j(String s) { try { return m.readTree(s); } catch (Exception e) { throw new RuntimeException(e); } }

    @Test void validatesLikeMultipleChoice() {
        var cfg = j("{\"choices\":[\"cat\",\"dog\"]}");
        t.validateAnswer(cfg, j("{\"choice\":\"cat\"}"));
        assertThatThrownBy(() -> t.validateAnswer(cfg, j("{\"choice\":\"bird\"}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void aggregateMajority() {
        var cfg = j("{\"choices\":[\"a\",\"b\"]}");
        JsonNode agg = t.aggregate(cfg, List.of(
                j("{\"choice\":\"a\"}"), j("{\"choice\":\"a\"}"), j("{\"choice\":\"b\"}")));
        assertThat(agg.get("choice").asText()).isEqualTo("a");
    }
}
