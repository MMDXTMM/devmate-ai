package com.devmate.tool.builtin;

import com.devmate.agent.config.ReviewAgentProperties;
import com.devmate.review.dto.ReviewDiffResponse;
import com.devmate.review.dto.ReviewFileResponse;
import com.devmate.review.service.ReviewDiffStateService;
import com.devmate.tool.model.AgentToolResult;
import com.devmate.tool.model.ReviewAgentContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GetDiffCoverageToolTest {

    @Test
    void trimsLargeDiffToConfiguredToolOutputLimit() {
        ReviewDiffStateService stateService = mock(ReviewDiffStateService.class);
        ReviewAgentProperties properties = new ReviewAgentProperties();
        properties.setMaxDiffFiles(50);
        properties.setMaxToolOutputCharacters(1_500);
        ReviewAgentContext context = new ReviewAgentContext(1L, 2L, 3L, 4L, "target");
        List<ReviewFileResponse> files = IntStream.range(0, 50)
                .mapToObj(index -> new ReviewFileResponse(
                        (long) index,
                        "src/main/java/com/example/feature" + index + "/OldService.java",
                        "src/main/java/com/example/feature" + index + "/NewService.java",
                        "MODIFY", "FULL", 10, 2, List.of(), List.of(), List.of(), null
                ))
                .toList();
        given(stateService.getByTask(1L, 3L)).willReturn(new ReviewDiffResponse(
                3L, 1L, "base", "target", "SUCCEEDED", 50, 50, 0, 0,
                null, LocalDateTime.now(), LocalDateTime.now(), files
        ));
        GetDiffCoverageTool tool = new GetDiffCoverageTool(
                stateService, properties, new ObjectMapper()
        );

        AgentToolResult result = tool.execute(context, new ObjectMapper().createObjectNode());

        assertThat(result.succeeded()).isTrue();
        assertThat(result.content()).hasSizeLessThanOrEqualTo(1_500);
        assertThat(result.content()).contains("\"truncated\":true");
        assertThat(result.resultSummary()).contains("changedFiles=50");
    }
}
