package com.devmate.generation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class GenerationSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsClarifyingSessionFromOneSentence() throws Exception {
        mockMvc.perform(post("/api/generation-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requirement":"做一个企业设备维修工单系统"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.status").value("CLARIFYING"))
                .andExpect(jsonPath("$.data.latestVersionNo").value(1))
                .andExpect(jsonPath("$.data.latestSpec.id").isString())
                .andExpect(jsonPath("$.data.latestSpec.questions.length()").value(4))
                .andExpect(jsonPath("$.data.latestSpec.promptVersion").value("guided-requirement-v2"))
                .andExpect(jsonPath("$.data.latestSpec.questions[0].category").value("BUSINESS"))
                .andExpect(jsonPath("$.data.latestSpec.questions[0].inputType").value("MULTIPLE_CHOICE"))
                .andExpect(jsonPath("$.data.latestSpec.questions[0].legacy").value(false))
                .andExpect(jsonPath("$.data.latestSpec.questions[0].options.length()").value(3))
                .andExpect(jsonPath("$.data.latestSpec.questions[1].inputType").value("SINGLE_CHOICE"))
                .andExpect(jsonPath("$.data.latestSpec.questions[1].options[0].recommended").value(true))
                .andExpect(jsonPath("$.data.latestSpec.questions[3].inputType").value("FREE_TEXT"));
    }

    @Test
    void storesSelectedRecommendedAiDefaultedAndCustomAnswers() throws Exception {
        JsonNode created = createSession("做一个企业设备维修工单系统");
        String sessionId = created.path("data").path("id").asText();

        mockMvc.perform(post("/api/generation-sessions/{sessionId}/clarifications", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[
                                  {"questionId":"target-users","decisionMode":"AI_DEFAULTED","selectedOptionIds":[]},
                                  {"questionId":"core-workflow","decisionMode":"USER_ACCEPTED_RECOMMENDATION","selectedOptionIds":["reporter-confirm"]},
                                  {"questionId":"business-rules","decisionMode":"USER_SELECTED","selectedOptionIds":["unique-request","state-transition"],"customAnswer":"关闭工单必须填写原因"},
                                  {"questionId":"external-integration","decisionMode":"CUSTOM","selectedOptionIds":[],"customAnswer":"第一版接入企业微信通知"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestVersionNo").value(2))
                .andExpect(jsonPath("$.data.latestSpec.answers.length()").value(4))
                .andExpect(jsonPath("$.data.latestSpec.answers[0].decisionMode").value("AI_DEFAULTED"))
                .andExpect(jsonPath("$.data.latestSpec.answers[0].selectedOptionIds.length()").value(3))
                .andExpect(jsonPath("$.data.latestSpec.answers[0].answer").value(
                        org.hamcrest.Matchers.startsWith("由 AI 按推荐方案决定：")
                ))
                .andExpect(jsonPath("$.data.latestSpec.answers[1].decisionMode").value(
                        "USER_ACCEPTED_RECOMMENDATION"
                ))
                .andExpect(jsonPath("$.data.latestSpec.answers[2].answer").value(
                        org.hamcrest.Matchers.containsString("关闭工单必须填写原因")
                ))
                .andExpect(jsonPath("$.data.latestSpec.answers[3].decisionMode").value("CUSTOM"));
    }

    @Test
    void rejectsInvalidStructuredSelections() throws Exception {
        JsonNode created = createSession("做一个企业设备维修工单系统");
        String sessionId = created.path("data").path("id").asText();

        expectClarificationFailure(sessionId, """
                {"answers":[{"questionId":"core-workflow","decisionMode":"USER_SELECTED","selectedOptionIds":["missing"]}]}
                """, "存在无法识别的选项");
        expectClarificationFailure(sessionId, """
                {"answers":[{"questionId":"core-workflow","decisionMode":"USER_SELECTED","selectedOptionIds":["reporter-confirm","handler-complete"]}]}
                """, "单选问题只能选择一个方案");
        expectClarificationFailure(sessionId, """
                {"answers":[{"questionId":"business-rules","decisionMode":"USER_SELECTED","selectedOptionIds":["unique-request","unique-request"]}]}
                """, "同一个选项不能重复提交");
        expectClarificationFailure(sessionId, """
                {"answers":[{"questionId":"external-integration","decisionMode":"AI_DEFAULTED","selectedOptionIds":[]}]}
                """, "自由文本问题需要填写自定义答案");
    }

    @Test
    void readsAndUpdatesLegacyV1QuestionData() throws Exception {
        JsonNode created = createSession("做一个预约系统");
        String sessionId = created.path("data").path("id").asText();
        long versionId = created.path("data").path("latestSpec").path("id").asLong();
        jdbcTemplate.update(
                "UPDATE generation_spec_version SET questions_json = ?, prompt_version = ? WHERE id = ?",
                "[{\"id\":\"legacy-role\",\"question\":\"旧版角色问题\",\"reason\":\"兼容验证\",\"required\":true}]",
                "guided-requirement-v1",
                versionId
        );

        mockMvc.perform(get("/api/generation-sessions/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestSpec.questions[0].inputType").value("FREE_TEXT"))
                .andExpect(jsonPath("$.data.latestSpec.questions[0].legacy").value(true))
                .andExpect(jsonPath("$.data.latestSpec.questions[0].options.length()").value(0));

        mockMvc.perform(post("/api/generation-sessions/{sessionId}/clarifications", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[{"questionId":"legacy-role","answer":"管理员和普通员工"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestSpec.answers[0].decisionMode").value("LEGACY_TEXT"))
                .andExpect(jsonPath("$.data.latestSpec.answers[0].answer").value("管理员和普通员工"));
    }

    @Test
    void rejectsBlankAndOutOfScopeRequirements() throws Exception {
        mockMvc.perform(post("/api/generation-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请用一句话描述你想创建的项目"));

        mockMvc.perform(post("/api/generation-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"做一个实时音视频平台\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "第一版暂不支持“实时音视频”类项目，请先使用管理或业务流程型 Spring Boot 后端需求"
                ));
    }

    @Test
    void clarifiesAndConfirmsLatestSpecVersion() throws Exception {
        JsonNode created = createSession("做一个库存出入库管理系统");
        String sessionId = created.path("data").path("id").asText();

        String clarificationJson = """
                {
                  "answers": [
                    {"questionId":"target-users","answer":"仓库管理员维护库存，业务员只读查询"},
                    {"questionId":"core-workflow","answer":"草稿、已提交、已审核、已完成"},
                    {"questionId":"business-rules","answer":"同一业务单号不能重复提交"}
                  ]
                }
                """;
        String clarifiedBody = mockMvc.perform(post(
                                "/api/generation-sessions/{sessionId}/clarifications", sessionId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clarificationJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestVersionNo").value(2))
                .andExpect(jsonPath("$.data.latestSpec.answers.length()").value(3))
                .andExpect(jsonPath("$.data.latestSpec.requirementSummary").value(
                        org.hamcrest.Matchers.containsString("同一业务单号不能重复提交")
                ))
                .andReturn().getResponse().getContentAsString();
        JsonNode clarified = objectMapper.readTree(clarifiedBody);
        String versionId = clarified.path("data").path("latestSpec").path("id").asText();

        mockMvc.perform(post("/api/generation-sessions/{sessionId}/confirmations", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":\"" + versionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedVersionId").value(versionId))
                .andExpect(jsonPath("$.data.latestSpec.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/generation-sessions/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.latestVersionNo").value(2));
    }

    @Test
    void refusesConfirmationUntilRequiredQuestionsAreAnswered() throws Exception {
        JsonNode created = createSession("做一个预约管理系统");
        String sessionId = created.path("data").path("id").asText();
        String versionId = created.path("data").path("latestSpec").path("id").asText();

        mockMvc.perform(post("/api/generation-sessions/{sessionId}/confirmations", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":\"" + versionId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.startsWith("还有必答问题未完成")
                ));
    }

    @Test
    void refusesUnknownQuestionAndModificationAfterConfirmation() throws Exception {
        JsonNode created = createSession("做一个内容发布管理系统");
        String sessionId = created.path("data").path("id").asText();

        mockMvc.perform(post("/api/generation-sessions/{sessionId}/clarifications", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[{"questionId":"unknown","answer":"test"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("存在无法识别的澄清问题"));

        String clarifiedBody = mockMvc.perform(post(
                                "/api/generation-sessions/{sessionId}/clarifications", sessionId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[
                                  {"questionId":"target-users","answer":"编辑和管理员"},
                                  {"questionId":"core-workflow","answer":"草稿到发布"},
                                  {"questionId":"business-rules","answer":"标题不能为空"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String versionId = objectMapper.readTree(clarifiedBody)
                .path("data").path("latestSpec").path("id").asText();
        mockMvc.perform(post("/api/generation-sessions/{sessionId}/confirmations", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":\"" + versionId + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/generation-sessions/{sessionId}/clarifications", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[{"questionId":"target-users","answer":"修改答案"}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("已确认的需求方案不能继续修改"));
    }

    @Test
    void createsANewVersionWithoutKeepingSupersededAnswerText() throws Exception {
        JsonNode created = createSession("做一个预约管理系统");
        String sessionId = created.path("data").path("id").asText();
        String initialAnswers = """
                {"answers":[
                  {"questionId":"target-users","answer":"旧角色描述"},
                  {"questionId":"core-workflow","answer":"预约到完成"},
                  {"questionId":"business-rules","answer":"同一时段不可重复"}
                ]}
                """;
        mockMvc.perform(post("/api/generation-sessions/{sessionId}/clarifications", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initialAnswers))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/generation-sessions/{sessionId}/clarifications", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[{"questionId":"target-users","answer":"新角色描述"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestVersionNo").value(3))
                .andExpect(jsonPath("$.data.latestSpec.requirementSummary").value(
                        org.hamcrest.Matchers.containsString("新角色描述")
                ))
                .andExpect(jsonPath("$.data.latestSpec.requirementSummary").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("旧角色描述"))
                ));
    }

    private JsonNode createSession(String requirement) throws Exception {
        String body = mockMvc.perform(post("/api/generation-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MapBody.of(requirement))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.path("data").path("id").asText()).isNotBlank();
        return json;
    }

    private void expectClarificationFailure(
            String sessionId,
            String requestBody,
            String expectedMessage
    ) throws Exception {
        mockMvc.perform(post("/api/generation-sessions/{sessionId}/clarifications", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(expectedMessage));
    }

    private record MapBody(String requirement) {
        static MapBody of(String requirement) {
            return new MapBody(requirement);
        }
    }
}
