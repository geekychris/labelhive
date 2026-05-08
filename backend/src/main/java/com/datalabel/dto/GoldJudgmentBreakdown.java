package com.datalabel.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One row of "what workers picked" for a gold unit. The admin UI groups gold
 * judgments by answer-shape so the table can show <code>yes ×2 ✓ · no ×1 ✗</code>
 * without having to render every individual judgment.
 */
public record GoldJudgmentBreakdown(JsonNode answer, boolean correct, int count) {}
