package com.devmate.conversation.service;

import com.devmate.conversation.entity.ConversationMessage;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ConversationHistoryBudgetTest {
    @Test void retainsRecentCompletePairsAndCurrentQuestionWithinBudget() {
        var first=message("USER","first");var large=message("ASSISTANT","x".repeat(2000));
        var recent=message("USER","recent");var answer=message("ASSISTANT","answer");var current=message("USER","current");
        assertThat(ConversationHistoryBudget.select(List.of(first,large,recent,answer,current),1000))
                .containsExactly(recent,answer,current);
    }
    @Test void neverTruncatesCurrentQuestionAndDropsOrphanAssistant() {
        var current=message("USER","x".repeat(2000));
        assertThat(ConversationHistoryBudget.select(List.of(message("ASSISTANT","orphan"),current),1000)).containsExactly(current);
        assertThat(ConversationHistoryBudget.select(List.of(),1000)).isEmpty();
    }
    private ConversationMessage message(String role,String content){var value=new ConversationMessage();value.setMessageRole(role);value.setContent(content);return value;}
}
