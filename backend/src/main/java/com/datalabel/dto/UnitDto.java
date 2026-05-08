package com.datalabel.dto;

import com.datalabel.domain.Job;
import com.datalabel.domain.Unit;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Worker-safe unit view: NEVER reveals gold-ness to the worker.
 * Use {@link AdminUnitDto} for admin views that include the gold answer.
 */
public record UnitDto(
        Long id,
        Long jobId,
        String templateType,
        JsonNode templateConfig,
        JsonNode payload,
        String displayTemplate
) {
    public static UnitDto forWorker(Unit u, Job job) {
        return new UnitDto(u.getId(), u.getJobId(),
                job.getTemplateType(), job.getTemplateConfig(),
                u.getPayload(), job.getDisplayTemplate());
    }
}
