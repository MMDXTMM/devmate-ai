package com.devmate.agent.model;

import com.devmate.agent.service.*;
import com.devmate.generation.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SpringAiGenerationModelTest {
    final ObjectMapper json=new ObjectMapper();
    final ModelConnectionSnapshot snapshot=new ModelConnectionSnapshot("COMPATIBLE","sample","https://model.example/v1","test-key");
    String envelope(Object content) throws Exception {
        var root=json.createObjectNode(); root.put("id","generation-test");root.put("object","chat.completion");root.put("created",1);root.put("model","sample");
        var choice=root.putArray("choices").addObject(); choice.put("index",0);choice.put("finish_reason","stop"); choice.putObject("message").put("role","assistant").put("content",json.writeValueAsString(content));return json.writeValueAsString(root);
    }
    @Test void asksForLanguageAfterBusinessAndPreservesHistoricalQuestions() throws Exception {
        var connection=mock(ModelConnectionService.class); when(connection.requireActiveConnection()).thenReturn(snapshot);
        var builder=RestClient.builder(); var server=MockRestServiceServer.bindTo(builder).build();
        var option=new RequirementOption("python","Python","容易开始","适合CLI",true);
        var question=new RequirementQuestion("language",RequirementQuestionCategory.TECHNICAL,RequirementInputType.SINGLE_CHOICE,"用哪种编程语言？","影响框架","Python","适合脚本",List.of(option,new RequirementOption("typescript","TypeScript","适合网页","需要Node",false)),true,true,false);
        var output=new SpringAiGenerationModel.DraftContent("待办CLI","语言待选择",List.of("本地工具"),List.of(question),false);
        server.expect(requestTo("https://model.example/v1/chat/completions")).andExpect(content().string(org.hamcrest.Matchers.containsString("LANGUAGE"))).andRespond(withSuccess(envelope(output),MediaType.APPLICATION_JSON));
        var model=new SpringAiGenerationModel(connection,new SpringAiChatClientFactory(builder,false),json);
        var previous=new RequirementDraft("待办","待选",List.of(),List.of(new RequirementQuestion("g1","用途","确认",true)),"project-builder-v1","BUSINESS",false);
        var revised=model.draft("待办CLI",previous,List.of(new RequirementAnswer("g1","个人使用")));
        assertThat(revised.stage()).isEqualTo("LANGUAGE");assertThat(revised.questions()).hasSize(2);assertThat(revised.questions().get(1).id()).isEqualTo("g2");assertThat(revised.readyForGeneration()).isFalse();server.verify();
    }
    @Test void rejectsMissingChoicesRatherThanPretendingAPlanWasGenerated() throws Exception {
        var connection=mock(ModelConnectionService.class);when(connection.requireActiveConnection()).thenReturn(snapshot);
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://model.example/v1/chat/completions")).andRespond(withSuccess(envelope(new SpringAiGenerationModel.DraftContent("summary","architecture",List.of(),List.of(),true)),MediaType.APPLICATION_JSON));
        var model=new SpringAiGenerationModel(connection,new SpringAiChatClientFactory(builder,false),json);
        assertThatThrownBy(()->model.draft("a project",null,List.of())).hasMessageContaining("选择题不完整");server.verify();
    }
}
