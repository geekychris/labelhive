package com.datalabel.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Worker-facing job instructions — task description plus a curated set of exemplar
 * units showing what a correct answer looks like (and optionally why).
 */
public record JobInstructions(
        Long jobId,
        String name,
        String description,
        String templateType,
        JsonNode templateConfig,
        List<Exemplar> exemplars
) {
    public record Exemplar(
            Long unitId,
            JsonNode payload,
            JsonNode goldAnswer,
            String goldExplanation
    ) {}
}
