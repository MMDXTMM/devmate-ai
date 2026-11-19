package com.devmate.conversation.service;

import com.devmate.conversation.entity.ConversationMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Keeps recent complete turns under a character budget; never cuts the current question. */
final class ConversationHistoryBudget {
    private ConversationHistoryBudget() { }
    static List<ConversationMessage> select(List<ConversationMessage> history, int characters) {
        if (history.isEmpty()) return List.of();
        List<ConversationMessage> selected = new ArrayList<>();
        var current = history.getLast(); selected.add(current);
        int used = current.getContent().length();
        for (int i = history.size()-2; i > 0; i-=2) {
            var assistant = history.get(i); var user = history.get(i-1);
            if (!"ASSISTANT".equals(assistant.getMessageRole()) || !"USER".equals(user.getMessageRole())) break;
            int size = assistant.getContent().length()+user.getContent().length();
            if (used + size > Math.max(1000, characters)) break;
            selected.add(assistant); selected.add(user); used+=size;
        }
        Collections.reverse(selected);
        return List.copyOf(selected);
    }
}
