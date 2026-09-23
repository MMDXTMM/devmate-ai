package com.devmate.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiGovernanceContractTest {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "delete", "patch", "head", "options"
    );
    private static final Set<String> PUBLIC_OPERATIONS = Set.of(
            "post /api/auth/register",
            "post /api/auth/login",
            "get /api/health"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void everyApiOperationHasBusinessSummaryAndMatchesSecurityAndIdRules() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(json);

        Iterator<Map.Entry<String, JsonNode>> paths = root.path("paths").fields();
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = paths.next();
            String path = pathEntry.getKey();
            Iterator<Map.Entry<String, JsonNode>> methods = pathEntry.getValue().fields();
            while (methods.hasNext()) {
                Map.Entry<String, JsonNode> methodEntry = methods.next();
                String method = methodEntry.getKey();
                if (!HTTP_METHODS.contains(method)) {
                    continue;
                }
                JsonNode operation = methodEntry.getValue();
                String operationKey = method + " " + path;
                assertThat(operation.path("summary").asText())
                        .as("%s must publish a business summary", operationKey)
                        .isNotBlank();

                if (PUBLIC_OPERATIONS.contains(operationKey)) {
                    assertThat(operation.path("security").isMissingNode()
                            || operation.path("security").isEmpty())
                            .as("%s must remain public", operationKey)
                            .isTrue();
                } else if (path.startsWith("/api/")) {
                    assertThat(operation.path("security").toString())
                            .as("%s must require bearerAuth", operationKey)
                            .contains("bearerAuth");
                    assertThat(operation.path("responses").has("401")).isTrue();
                    assertThat(operation.path("responses").has("403")).isTrue();
                }

                operation.path("parameters").forEach(parameter -> {
                    if (isIdName(parameter.path("name").asText())) {
                        assertThat(parameter.path("schema").path("type").asText())
                                .as("%s parameter %s must be a string",
                                        operationKey, parameter.path("name").asText())
                                .isEqualTo("string");
                    }
                });
            }
        }

        root.path("components").path("schemas").fields().forEachRemaining(schemaEntry ->
                schemaEntry.getValue().path("properties").fields().forEachRemaining(propertyEntry -> {
                    String propertyName = propertyEntry.getKey();
                    JsonNode property = propertyEntry.getValue();
                    if (isIdName(propertyName)) {
                        assertThat(property.path("type").asText())
                                .as("%s.%s must be a string", schemaEntry.getKey(), propertyName)
                                .isEqualTo("string");
                    } else if (propertyName.endsWith("Ids")) {
                        assertThat(property.path("items").path("type").asText())
                                .as("%s.%s items must be strings", schemaEntry.getKey(), propertyName)
                                .isEqualTo("string");
                    }
                })
        );

        assertThat(root.at("/components/schemas/ModelConnectionUpdateRequest/properties/apiKey/writeOnly")
                .asBoolean()).isTrue();
        assertThat(root.at("/components/schemas/CreateAiReviewRequest/properties/attemptKey/pattern")
                .asText()).startsWith("^").endsWith("$");
        assertThat(root.at("/components/schemas/CreateProjectUnderstandingReportRequest/properties/attemptKey/pattern")
                .asText()).startsWith("^").endsWith("$");
    }

    private boolean isIdName(String name) {
        return name.equals("id") || name.endsWith("Id");
    }
}
