package com.devmate.generation.service;

import com.devmate.generation.model.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@Profile("!test")
public class AiRequirementDraftProvider implements RequirementDraftProvider {
    private final GenerationModel model;
    public AiRequirementDraftProvider(GenerationModel model) { this.model = model; }
    public RequirementDraft createInitialDraft(String requirement) { return model.draft(requirement, null, List.of()); }
    public RequirementDraft reviseDraft(String requirement, RequirementDraft previous, List<RequirementAnswer> answers) {
        return model.draft(requirement, previous, answers);
    }
}
