package com.devmate.generation.service;

import com.devmate.generation.model.RequirementAnswer;
import com.devmate.generation.model.RequirementDraft;

import java.util.List;

public interface RequirementDraftProvider {

    RequirementDraft createInitialDraft(String originalRequirement);

    RequirementDraft reviseDraft(
            String originalRequirement,
            RequirementDraft previousDraft,
            List<RequirementAnswer> answers
    );
}
