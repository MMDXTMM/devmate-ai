package com.devmate.project.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publishesProjectCrudContractWithBearerAuthAndStringIds() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("DevMate AI API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.summary").value("登录"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/projects'].post.summary").value("创建项目"))
                .andExpect(jsonPath("$.paths['/api/projects'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/projects'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/projects/{projectId}'].get.parameters[0].schema.type")
                        .value("string"))
                .andExpect(jsonPath("$.components.schemas.ProjectResponse.properties.id.type")
                        .value("string"))
                .andExpect(jsonPath("$.components.schemas.CreateProjectRequest.properties.sourceType.enum[0]")
                        .value("LOCAL"))
                .andExpect(jsonPath("$.components.schemas.CreateProjectRequest.required")
                        .value(org.hamcrest.Matchers.hasItem("name")))
                .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.password.writeOnly")
                        .value(true))
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.id.type")
                        .value("string"));
    }
}
