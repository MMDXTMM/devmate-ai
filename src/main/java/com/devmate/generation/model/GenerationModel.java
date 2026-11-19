package com.devmate.generation.model;

import com.devmate.agent.model.ModelConnectionSnapshot;
import com.devmate.generation.dto.GenerationSpecResponse;
import java.util.List;

public interface GenerationModel {
    RequirementDraft draft(String requirement, RequirementDraft previous, List<RequirementAnswer> answers);
    ProjectErrorHelp explainError(ModelConnectionSnapshot connection, ProjectBundle project, String sanitizedError);
    ProjectLearningGuide learningGuide(ModelConnectionSnapshot connection, ProjectBundle project);
    ProjectChangePlan planChange(ModelConnectionSnapshot connection, ProjectBundle original, String request);
    ProjectBundle change(ModelConnectionSnapshot connection, ProjectBundle original, String request, ProjectChangePlan plan);
    ProjectBundle repair(ModelConnectionSnapshot connection, ProjectBundle original, VerificationResult failure);
    ProjectBundle generate(ModelConnectionSnapshot connection, GenerationSpecResponse spec);
}
