package com.devmate.generation.service;
import com.devmate.generation.model.ProjectBundle;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class GenerationRuntimeGuideServiceTest {
    final GenerationRuntimeGuideService service=new GenerationRuntimeGuideService(null,new ObjectMapper());
    @Test void identifiesRuntimeAndOnlyDisplaysConfigPathsNotValues(){
        var b=new ProjectBundle("todo","todo","run",List.of(new ProjectBundle.ProjectFile("main.py","pass"),new ProjectBundle.ProjectFile(".env.example","PASSWORD=private-value")),List.of());
        var guide=service.describe(b);assertThat(guide.language()).isEqualTo("Python");assertThat(guide.toString()).contains("python3 --version",".env.example").doesNotContain("private-value");
    }
    @Test void usesExistingNodeScriptsWithoutExecutingTheirContents(){
        var b=new ProjectBundle("todo","todo","run",List.of(new ProjectBundle.ProjectFile("package.json","{\"scripts\":{\"dev\":\"evil command\",\"test\":\"node --test\"}}")),List.of());
        var guide=service.describe(b);assertThat(guide.toString()).contains("npm run dev","npm test").doesNotContain("evil command");
    }
    @Test void redactsCommonCredentialsBeforeModelTransmission(){
        String raw="Authorization: Bearer bearer-private-value\npassword=private-pass api_key=private-key https://user:secret@example.com sk-abcdefghijklmnop123456\n{\"token\":\"private-token\"}";
        assertThat(DiagnosticRedactor.redact(raw)).doesNotContain("bearer-private-value","private-pass","private-key","user:secret","sk-abcdefghijklmnop123456","private-token").contains("[REDACTED]");
    }
}
