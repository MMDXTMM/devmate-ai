package com.devmate.user.controller;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.devmate.common.web.RequestCorrelationFilter;
import com.devmate.user.entity.AppUser;
import com.devmate.user.mapper.AppUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "devmate.security.enabled=true",
        "devmate.security.jwt-secret=test-jwt-secret-with-at-least-32-characters"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthenticationAndProjectAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserMapper appUserMapper;

    @Test
    void registersLogsInAndReturnsCurrentUserWithoutStoringPlainPassword() throws Exception {
        String token = register("alice", "alice@example.com");

        AppUser stored = appUserMapper.selectList(null).getFirst();
        assertThat(stored.getPasswordHash())
                .startsWith("$2")
                .isNotEqualTo("Password123");

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"Password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void rejectsDuplicateRegistrationAndWrongPassword() throws Exception {
        register("alice", "alice@example.com");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"Password456","email":"other@example.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void rejectsMissingAndTamperedTokensWhileHealthRemainsPublic() throws Exception {
        String token = register("alice", null);
        String expiredToken = JWT.create()
                .withIssuer("devmate-ai")
                .withSubject("1")
                .withClaim("username", "alice")
                .withExpiresAt(Instant.now().minusSeconds(60))
                .sign(Algorithm.HMAC256("test-jwt-secret-with-at-least-32-characters"));

        mockMvc.perform(get("/api/projects")
                        .header(RequestCorrelationFilter.REQUEST_ID_HEADER, "unauthorized-case-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        RequestCorrelationFilter.REQUEST_ID_HEADER,
                        "unauthorized-case-1"
                ))
                .andExpect(jsonPath("$.code").value(40100));

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", bearer(token + "tampered")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", bearer(expiredToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void createsOwnerMembershipAndIsolatesAllProjectRoutesBetweenUsers() throws Exception {
        String aliceToken = register("alice", "alice@example.com");
        String projectId = createProject(aliceToken, "alice-project");
        String bobToken = register("bob", "bob@example.com");

        mockMvc.perform(get("/api/projects").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(projectId));

        mockMvc.perform(get("/api/projects").header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        expectForbidden(get("/api/projects/{id}", projectId), bobToken);
        expectForbidden(put("/api/projects/{id}", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"hijacked","sourceType":"GIT","sourceLocation":"https://example.com/repo.git"}
                        """), bobToken);
        expectForbidden(delete("/api/projects/{id}", projectId), bobToken);
        expectForbidden(post("/api/projects/{id}/imports", projectId), bobToken);

        mockMvc.perform(get("/api/projects/{id}", projectId)
                        .header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("alice-project"));

        Number owners = jdbcOwnerCount(projectId);
        assertThat(owners.intValue()).isEqualTo(1);
    }

    private String register(String username, String email) throws Exception {
        String emailField = email == null ? "null" : "\"" + email + "\"";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123","email":%s}
                                """.formatted(username, emailField)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.id").isString())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data.accessToken"
        );
    }

    private String createProject(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "sourceType":"GIT",
                                  "sourceLocation":"https://github.com/example/%s.git",
                                  "defaultBranch":"main"
                                }
                                """.formatted(name, name)))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data.id"
        );
    }

    private void expectForbidden(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String token
    ) throws Exception {
        mockMvc.perform(request.header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Number jdbcOwnerCount(String projectId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_member WHERE project_id = ? AND member_role = 'OWNER'",
                Number.class,
                Long.valueOf(projectId)
        );
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
