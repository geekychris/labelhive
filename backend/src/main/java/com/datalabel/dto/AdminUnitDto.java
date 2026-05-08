package com.datalabel.dto;

import com.datalabel.domain.Unit;
import com.datalabel.domain.UnitSource;
import com.datalabel.domain.UnitStatus;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record AdminUnitDto(
        Long id, Long jobId, String externalId,
        JsonNode payload, JsonNode goldAnswer, String goldExplanation,
        boolean exemplar,
        UnitSource source, UnitStatus status, int judgmentCount,
        Long promotedByUserId, String promotedByUsername, Long promotedFromJudgmentId,
        Integer goldMatches,
        Integer goldMismatches,
        /** Distinct answers workers picked for a gold unit, with counts and correctness. */
        List<GoldJudgmentBreakdown> goldJudgmentBreakdown
) {
    public static AdminUnitDto from(Unit u) {
        return new AdminUnitDto(u.getId(), u.getJobId(), u.getExternalId(),
                u.getPayload(), u.getGoldAnswer(), u.getGoldExplanation(), u.isExemplar(),
                u.getSource(), u.getStatus(), u.getJudgmentCount(),
                u.getPromotedByUserId(), null, u.getPromotedFromJudgmentId(),
                null, null, null);
    }

    public static AdminUnitDto from(Unit u, String promotedByUsername) {
        return new AdminUnitDto(u.getId(), u.getJobId(), u.getExternalId(),
                u.getPayload(), u.getGoldAnswer(), u.getGoldExplanation(), u.isExemplar(),
                u.getSource(), u.getStatus(), u.getJudgmentCount(),
                u.getPromotedByUserId(), promotedByUsername, u.getPromotedFromJudgmentId(),
                null, null, null);
    }

    /** With denormalized username + per-unit gold agreement counts and breakdown. */
    public static AdminUnitDto from(Unit u, String promotedByUsername,
                                    Integer goldMatches, Integer goldMismatches,
                                    List<GoldJudgmentBreakdown> breakdown) {
        return new AdminUnitDto(u.getId(), u.getJobId(), u.getExternalId(),
                u.getPayload(), u.getGoldAnswer(), u.getGoldExplanation(), u.isExemplar(),
                u.getSource(), u.getStatus(), u.getJudgmentCount(),
                u.getPromotedByUserId(), promotedByUsername, u.getPromotedFromJudgmentId(),
                goldMatches, goldMismatches, breakdown);
    }
}
