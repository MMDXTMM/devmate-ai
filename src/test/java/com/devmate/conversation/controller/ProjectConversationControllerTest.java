package com.devmate.conversation.controller;

import com.devmate.conversation.service.ProjectConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectConversationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ProjectConversationService service;

    @Test
    void startsAuthenticatedPostSseStreamWithStringSafeIdentifiers() throws Exception {
        when(service.stream(any(), any(), any())).thenReturn(new SseEmitter(30_000L));

        mockMvc.perform(post("/api/projects/1/agent-conversations/2/messages/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "订单怎么创建？",
                                  "attemptKey": "123e4567-e89b-42d3-a456-426614174000"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    void rejectsInvalidAttemptKeyBeforeStartingStream() throws Exception {
        mockMvc.perform(post("/api/projects/1/agent-conversations/2/messages/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"订单怎么创建？","attemptKey":"not-a-uuid"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
