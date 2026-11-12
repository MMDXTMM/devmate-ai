package com.devmate.agent.model;

import com.devmate.agent.config.ProjectUnderstandingProperties;
import com.devmate.agent.service.SpringAiChatClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SpringAiProjectUnderstandingModelTest {

    @Test
    void advertisesExplicitModuleIconRecordsRatherThanDynamicMapKeysToTheModel() {
        String schema = new org.springframework.ai.converter.BeanOutputConverter<>(
                com.devmate.knowledge.dto.ProjectGuide.Content.class).getJsonSchema();
        assertThat(schema).contains("moduleId", "projectIcon").doesNotContain("moduleIcons");
    }

    private ProjectUnderstandingProperties legacyGuideProperties() {
        var properties = new ProjectUnderstandingProperties(); properties.setPromptVersion("project-guide-v6"); return properties;
    }

    @Test
    void mapsVersionedGuideContentThroughSpringAiWithoutAcceptingModelSourceLocations() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String contentJson = new String(getClass().getResourceAsStream("/guide/cli-guide.json").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        var contentTree = mapper.readTree(contentJson);
        var appearance = ((com.fasterxml.jackson.databind.node.ObjectNode) contentTree).putObject("appearance");
        appearance.put("projectIcon", "🔭"); appearance.putArray("modules").addObject().put("moduleId", "convert").put("icon", "🧮");
        contentJson = mapper.writeValueAsString(contentTree);
        var envelope = mapper.createObjectNode(); envelope.put("id", "guide-1"); envelope.put("object", "chat.completion");
        envelope.put("created", 1); envelope.put("model", "example-model");
        var choice = envelope.putArray("choices").addObject(); choice.put("index", 0); choice.put("finish_reason", "stop");
        choice.putObject("message").put("role", "assistant").put("content", contentJson);
        envelope.putObject("usage").put("prompt_tokens", 40).put("completion_tokens", 80).put("total_tokens", 120);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://model.example/v1/chat/completions"))
                .andRespond(withSuccess(mapper.writeValueAsString(envelope), MediaType.APPLICATION_JSON));
        var model = new SpringAiProjectUnderstandingModel(new ModelConnectionSnapshot("COMPATIBLE", "example-model",
                "https://model.example/v1", "test-key"), legacyGuideProperties(), new SpringAiChatClientFactory(builder, false));
        var result = model.analyze(new ProjectUnderstandingPrompt("请按 Content 契约输出", "evidenceId=42"));
        assertThat(result.guide().schemaVersion()).isEqualTo("project-guide-v1");
        assertThat(result.guide().content().project().type()).isEqualTo("Python CLI");
        assertThat(result.guide().content().appearance().projectIcon()).isEqualTo("🔭");
        assertThat(result.guide().content().appearance().modules()).containsExactly(new com.devmate.knowledge.dto.ProjectGuide.ModuleIcon("convert", "🧮"));
        assertThat(result.guide().snapshot()).isNull();
        assertThat(result.guide().evidence()).isEmpty();
        assertThat(result.totalTokens()).isEqualTo(120); server.verify();
    }

    @Test
    void mapsStructuredChineseReportAndUsageThroughSpringAi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://model.example/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("\"model\":\"qwen-plus\""),
                        org.hamcrest.Matchers.containsString("evidenceId=42"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("test-key"))
                )))
                .andRespond(withSuccess("""
                        {
                          "id":"understanding-1","object":"chat.completion","created":1,"model":"qwen-plus",
                          "choices":[{
                            "index":0,
                            "message":{"role":"assistant","content":"{\\\"executiveSummary\\\":\\\"订单系统\\\",\\\"architectureNarrative\\\":\\\"Controller调用Service\\\",\\\"businessFlows\\\":[{\\\"name\\\":\\\"创建订单\\\",\\\"goal\\\":\\\"保存订单\\\",\\\"steps\\\":[\\\"接收请求\\\"],\\\"apiEntries\\\":[\\\"POST /orders\\\"],\\\"dataChanges\\\":[\\\"新增订单\\\"],\\\"evidenceIds\\\":[\\\"42\\\"]}],\\\"readingGuide\\\":[{\\\"order\\\":1,\\\"title\\\":\\\"订单入口\\\",\\\"reason\\\":\\\"先看入口\\\",\\\"evidenceIds\\\":[\\\"42\\\"]}],\\\"risksAndUnknowns\\\":[\\\"库存规则待确认\\\"]}"},
                            "finish_reason":"stop"
                          }],
                          "usage":{"prompt_tokens":80,"completion_tokens":40,"total_tokens":120}
                        }
                        """, MediaType.APPLICATION_JSON));

        ProjectUnderstandingProperties properties = new ProjectUnderstandingProperties();
        properties.setPromptVersion("project-understanding-v2");
        SpringAiProjectUnderstandingModel model = new SpringAiProjectUnderstandingModel(
                new ModelConnectionSnapshot("DASHSCOPE", "qwen-plus", "https://model.example/v1", "test-key"),
                properties, new SpringAiChatClientFactory(builder, false)
        );
        ProjectUnderstandingModelResult result = model.analyze(new ProjectUnderstandingPrompt(
                "只基于证据", "evidenceId=42"
        ));

        assertThat(result.executiveSummary()).isEqualTo("订单系统");
        assertThat(result.businessFlows()).singleElement()
                .extracting(ProjectUnderstandingModelResult.BusinessFlow::evidenceIds)
                .isEqualTo(java.util.List.of("42"));
        assertThat(result.totalTokens()).isEqualTo(120);
        server.verify();
    }
}
