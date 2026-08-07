package com.devmate.common.health;

import com.devmate.common.web.RequestCorrelationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsApplicationHealth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        RequestCorrelationFilter.REQUEST_ID_HEADER,
                        org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")
                ))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.application").value("devmate-ai"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void returnsSafeClientRequestIdOnBusinessError() throws Exception {
        mockMvc.perform(get("/api/projects/0")
                        .header(RequestCorrelationFilter.REQUEST_ID_HEADER, "support-case-123"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(
                        RequestCorrelationFilter.REQUEST_ID_HEADER,
                        "support-case-123"
                ))
                .andExpect(jsonPath("$.code").value(40000));
    }
}
