package com.datalabel.service;

import com.datalabel.template.types.SearchRelevanceTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SearchRelevanceTemplateTest {
    private final ObjectMapper m = new ObjectMapper();
    private final SearchRelevanceTemplate t = new SearchRelevanceTemplate();

    private JsonNode j(String s) { try { return m.readTree(s); } catch (Exception e) { throw new RuntimeException(e); } }

    @Test void ordinalPartialAgreement() {
        var cfg = j("{\"scale\":[{\"label\":\"a\",\"grade\":0},{\"label\":\"b\",\"grade\":1},{\"label\":\"c\",\"grade\":2},{\"label\":\"d\",\"grade\":3}]}");
        // Off-by-one on a 0..3 scale → 1 - 1/3 ≈ 0.667
        assertThat(t.agreementScore(cfg, j("{\"grade\":2}"), j("{\"grade\":3}")))
                .isCloseTo(0.667, within(0.01));
        // Identical → 1.0
        assertThat(t.agreementScore(cfg, j("{\"grade\":2}"), j("{\"grade\":2}"))).isEqualTo(1.0);
        // Worst case → 0.0
        assertThat(t.agreementScore(cfg, j("{\"grade\":0}"), j("{\"grade\":3}"))).isEqualTo(0.0);
    }
}
