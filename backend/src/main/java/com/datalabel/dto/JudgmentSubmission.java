package com.datalabel.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record JudgmentSubmission(JsonNode answer, Integer timeMs) {}
