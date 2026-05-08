package com.datalabel.controller;

import com.datalabel.sdk.TemplateRegistry;
import com.datalabel.template.types.MultipleChoiceTemplate;
import com.datalabel.template.types.SearchRelevanceTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TemplateControllerTest {

    private MockMvc mvc;

    @BeforeEach void setup() {
        var registry = new TemplateRegistry(List.of(new MultipleChoiceTemplate(), new SearchRelevanceTemplate()));
        mvc = MockMvcBuilders.standaloneSetup(new TemplateController(registry))
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test void listsRegisteredTypes() throws Exception {
        mvc.perform(get("/api/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].type", org.hamcrest.Matchers.containsInAnyOrder(
                        "MULTIPLE_CHOICE", "SEARCH_RELEVANCE")));
    }

    @Test void exposesVariantSchemaForMultipleChoice() throws Exception {
        mvc.perform(get("/api/templates/MULTIPLE_CHOICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("MULTIPLE_CHOICE"))
                .andExpect(jsonPath("$.variantSchema.fields[0].name").value("choices"))
                .andExpect(jsonPath("$.variantSchema.fields[0].kind").value("string-list"))
                .andExpect(jsonPath("$.variantSchema.fields[0].required").value(true))
                .andExpect(jsonPath("$.variantSchema.fields[1].name").value("allowMultiple"))
                .andExpect(jsonPath("$.variantSchema.fields[1].kind").value("boolean"))
                .andExpect(jsonPath("$.variantSchema.fields[1].default").value(false));
    }

    @Test void searchRelevanceSchemaIncludesScaleField() throws Exception {
        mvc.perform(get("/api/templates/SEARCH_RELEVANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantSchema.fields[?(@.name=='scale')].kind").value("scale"))
                .andExpect(jsonPath("$.variantSchema.fields[?(@.name=='scale')].default[0].label").value("irrelevant"));
    }

    @Test void unknownTypeIs400() throws Exception {
        mvc.perform(get("/api/templates/NONEXISTENT"))
                .andExpect(status().is4xxClientError());
    }

    @Test void exposesPayloadSchemaWithExample() throws Exception {
        mvc.perform(get("/api/templates/MULTIPLE_CHOICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payloadSchema.requiredFields[0]").value("text"))
                .andExpect(jsonPath("$.payloadSchema.examplePayload.text").exists())
                .andExpect(jsonPath("$.payloadSchema.exampleAnswer.choice").exists());
    }

    @Test void validateDatasetReturnsPerRowErrors() throws Exception {
        // 3 units: 2 valid (one with gold, one without), 1 with bad gold.
        String body = """
            {
              "config": {"choices": ["yes","no"]},
              "units": [
                { "payload": {"text": "ok"}, "goldAnswer": {"choice": "yes"} },
                { "payload": {"text": "open"} },
                { "payload": {"text": "bad"}, "goldAnswer": {"choice": "maybe"} }
              ]
            }
            """;
        mvc.perform(post("/api/templates/MULTIPLE_CHOICE/validate-dataset")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.summary.total").value(3))
                .andExpect(jsonPath("$.summary.valid").value(2))
                .andExpect(jsonPath("$.summary.invalid").value(1))
                .andExpect(jsonPath("$.summary.withGold").value(1))
                .andExpect(jsonPath("$.errors[0].index").value(2))
                .andExpect(jsonPath("$.errors[0].error", org.hamcrest.Matchers.containsString("invalid choice")));
    }

    @Test void validateDatasetReturnsOkForCleanDataset() throws Exception {
        String body = """
            {
              "config": {"choices": ["yes","no"]},
              "units": [
                { "payload": {"text": "a"}, "goldAnswer": {"choice": "yes"} },
                { "payload": {"text": "b"} }
              ]
            }
            """;
        mvc.perform(post("/api/templates/MULTIPLE_CHOICE/validate-dataset")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.errors.length()").value(0));
    }

    @Test void validateDatasetRejectsBadConfig() throws Exception {
        String body = """
            { "config": {}, "units": [] }
            """;
        mvc.perform(post("/api/templates/MULTIPLE_CHOICE/validate-dataset")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.errors[0].at").value("config"));
    }
}
