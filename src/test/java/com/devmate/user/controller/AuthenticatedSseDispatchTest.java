package com.devmate.user.controller;

import com.devmate.conversation.service.ProjectConversationService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "devmate.security.enabled=true",
        "devmate.security.jwt-secret=test-jwt-secret-with-at-least-32-characters"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthenticatedSseDispatchTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ProjectConversationService service;

    @Test
    void completesAuthenticatedStreamAcrossAsyncRedispatch() throws Exception {
        String token = register("stream_owner");
        String projectId = createProject(token);
        SseEmitter emitter = new SseEmitter(10_000L);
        when(service.stream(any(), any(), any())).thenReturn(emitter);

        var result = mockMvc.perform(stream(projectId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn();
        emitter.send(SseEmitter.event().name("done").data("completed"));
        emitter.complete();
        SecurityContextHolder.clearContext();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));
    }

    @Test
    void rejectsUnauthenticatedAndOtherAccountsBeforeStartingStream() throws Exception {
        String owner = register("stream_owner");
        String projectId = createProject(owner);
        String other = register("stream_other");
        mockMvc.perform(stream(projectId)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
        mockMvc.perform(stream(projectId).header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40300));
        verifyNoInteractions(service);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder stream(String projectId) {
        return post("/api/projects/" + projectId + "/agent-conversations/2/messages/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"订单流程\",\"attemptKey\":\"123e4567-e89b-42d3-a456-426614174000\"}");
    }

    private String register(String username) throws Exception {
        var result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private String createProject(String token) throws Exception {
        var result = mockMvc.perform(post("/api/projects").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"stream-project\",\"sourceType\":\"GIT\",\"sourceLocation\":\"https://github.com/example/test.git\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }
}
