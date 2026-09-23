package com.devmate.review.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewWorkflowOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publishesIdempotencyLifecycleRecoveryAndAuthorizationContract() throws Exception {
        String path = "$.paths['/api/projects/{projectId}/review-workflows']";
        String latestPath = "$.paths['/api/projects/{projectId}/review-workflows/latest']";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".post.summary").value("执行一键代码审查"))
                .andExpect(jsonPath(path + ".post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath(path + ".post.responses['200']").exists())
                .andExpect(jsonPath(path + ".post.responses['400']").exists())
                .andExpect(jsonPath(path + ".post.responses['409']").exists())
                .andExpect(jsonPath(path + ".post.parameters[0].schema.type").value("string"))
                .andExpect(jsonPath(latestPath + ".get.summary").value("查询最近一次代码审查"))
                .andExpect(jsonPath(latestPath + ".get.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.CreateReviewWorkflowRequest.required")
                        .value(hasItems("attemptKey")))
                .andExpect(jsonPath("$.components.schemas.CreateReviewWorkflowRequest.properties.attemptKey.pattern")
                        .value("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"))
                .andExpect(jsonPath("$.components.schemas.ReviewWorkflowResponse.properties.id.type")
                        .value("string"))
                .andExpect(jsonPath("$.components.schemas.ReviewWorkflowResponse.properties.status.enum[*]")
                        .value(containsInAnyOrder("RUNNING", "SUCCEEDED", "FAILED")))
                .andExpect(jsonPath("$.components.schemas.ReviewWorkflowResponse.properties.currentStage.enum[*]")
                        .value(containsInAnyOrder(
                                "SOURCE_IMPORT", "DIFF", "STATIC_ANALYSIS",
                                "EMBEDDING", "AGENT_REVIEW", "COMPLETED"
                        )));
    }
}
