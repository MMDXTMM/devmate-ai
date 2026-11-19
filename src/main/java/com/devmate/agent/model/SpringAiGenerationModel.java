package com.devmate.agent.model;

import com.devmate.agent.service.ModelConnectionService;
import com.devmate.agent.service.SpringAiChatClientFactory;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.generation.dto.GenerationSpecResponse;
import com.devmate.generation.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Component
public class SpringAiGenerationModel implements GenerationModel {
    private final ModelConnectionService connections;
    private final SpringAiChatClientFactory clients;
    private final ObjectMapper json;

    public SpringAiGenerationModel(ModelConnectionService connections, SpringAiChatClientFactory clients, ObjectMapper json) {
        this.connections = connections; this.clients = clients; this.json = json;
    }

    @Override
    public RequirementDraft draft(String requirement, RequirementDraft previous, List<RequirementAnswer> answers) {
        String stage = previous == null ? "BUSINESS" : switch (previous.stage()) {
            case "BUSINESS" -> "LANGUAGE";
            case "LANGUAGE" -> "TECHNOLOGY";
            case "TECHNOLOGY" -> "DETAILS";
            default -> "REVIEW";
        };
        DraftContent result = call(connections.requireActiveConnection(), "project-builder-questions-v1.txt",
                encode(Map.of("stage", stage, "originalRequirement", requirement,
                        "previous", previous == null ? Map.of() : previous, "answers", answers)), DraftContent.class, 7000);
        if (result == null || !text(result.requirementSummary(), 12000) || !text(result.architectureSummary(), 12000)
                || result.assumptions() == null || result.assumptions().size() > 12 || result.questions() == null
                || result.questions().size() > 3) throw invalidOutput();
        boolean ready = "REVIEW".equals(stage) && result.readyForGeneration() && result.questions().isEmpty();
        if (!ready && result.questions().isEmpty()) throw invalidOutput();
        List<RequirementQuestion> questions = new ArrayList<>(previous == null ? List.of() : previous.questions());
        int count = questions.size();
        for (int i = 0; i < result.questions().size(); i++) {
            RequirementQuestion q = result.questions().get(i);
            if (q == null || q.inputType() == RequirementInputType.FREE_TEXT || !text(q.question(), 600)
                    || !text(q.reason(), 1200) || !text(q.aiRecommendation(), 1200) || !text(q.recommendationReason(), 1200)
                    || q.options().size() < 2 || q.options().size() > 4) throw invalidOutput();
            Set<String> optionIds = new HashSet<>();
            for (RequirementOption option : q.options()) {
                if (option == null || !text(option.id(), 80) || !optionIds.add(option.id()) || !text(option.label(), 200)
                        || !text(option.description(), 1000) || !text(option.impact(), 1000)) throw invalidOutput();
            }
            long recommended = q.options().stream().filter(RequirementOption::recommended).count();
            if (recommended == 0 || q.inputType() == RequirementInputType.SINGLE_CHOICE && recommended != 1) throw invalidOutput();
            questions.add(new RequirementQuestion("g" + (count + i + 1), q.category(), q.inputType(), q.question(), q.reason(),
                    q.aiRecommendation(), q.recommendationReason(), q.options(), true, true, false));
        }
        return new RequirementDraft(result.requirementSummary(), result.architectureSummary(), result.assumptions(),
                List.copyOf(questions), "project-builder-v1", stage, ready);
    }

    @Override
    public ProjectErrorHelp explainError(ModelConnectionSnapshot connection,ProjectBundle project,String sanitizedError) {
        return call(connection,"project-builder-error-help-v1.txt",encode(Map.of("project",project,"error",sanitizedError)),ProjectErrorHelp.class,5500);
    }
    @Override
    public ProjectLearningGuide learningGuide(ModelConnectionSnapshot connection,ProjectBundle project) {
        return call(connection,"project-builder-guide-v1.txt",encode(project),ProjectLearningGuide.class,6500);
    }
    @Override
    public ProjectChangePlan planChange(ModelConnectionSnapshot connection, ProjectBundle original, String request) {
        var result=call(connection,"project-builder-change-plan-v1.txt",encode(Map.of("original",original,"request",request)),ProjectChangePlan.class,5000);
        if(result==null || !text(result.summary(),4000) || result.changes()==null || result.changes().isEmpty() || result.changes().size()>15 || result.risks()==null || result.risks().size()>10) throw invalidOutput();
        return result;
    }
    @Override
    public ProjectBundle change(ModelConnectionSnapshot connection, ProjectBundle original, String request, ProjectChangePlan plan) {
        return call(connection,"project-builder-change-code-v1.txt",encode(Map.of("original",original,"request",request,"plan",plan)),ProjectBundle.class,24000);
    }
    @Override
    public ProjectBundle repair(ModelConnectionSnapshot connection, ProjectBundle original, VerificationResult failure) {
        return call(connection, "project-builder-repair-v1.txt", encode(Map.of("original",original,"failure",failure)), ProjectBundle.class,24000);
    }

    @Override
    public ProjectBundle generate(ModelConnectionSnapshot connection, GenerationSpecResponse spec) {
        return call(connection, "project-builder-code-v1.txt", encode(spec), ProjectBundle.class, 24000);
    }

    private <T> T call(ModelConnectionSnapshot connection, String prompt, String input, Class<T> type, int maxTokens) {
        try {
            var options = OpenAiChatOptions.builder().model(connection.model()).maxTokens(maxTokens);
            if ("DASHSCOPE".equals(connection.provider())) options.extraBody(Map.of("enable_thinking", false));
            String system = new ClassPathResource("prompts/" + prompt).getContentAsString(StandardCharsets.UTF_8);
            com.devmate.generation.service.GenerationTaskService.checkCancelled();
            com.devmate.generation.service.GenerationTaskService.modelStarted();
            var response=clients.createChatClient(connection, options.build(), Duration.ofSeconds(5), Duration.ofSeconds(180))
                    .prompt().system(system).user(input).call().responseEntity(type);
            com.devmate.generation.service.GenerationTaskService.usage(response.response()==null?null:response.response().getMetadata().getUsage());
            com.devmate.generation.service.GenerationTaskService.checkCancelled();
            return response.entity();
        } catch (IOException exception) {
            throw new IllegalStateException("生成提示词无法加载", exception);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    SpringAiErrorTranslator.reviewFailure("项目生成 AI 调用", exception).getMessage());
        }
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("生成上下文无法编码", exception); }
    }
    private boolean text(String value, int max) { return value != null && !value.isBlank() && value.length() <= max; }
    private BusinessException invalidOutput() { return new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 返回的选择题不完整，请重试；上一版方案仍然保留"); }
    record DraftContent(String requirementSummary, String architectureSummary, List<String> assumptions,
                        List<RequirementQuestion> questions, boolean readyForGeneration) { }
}
