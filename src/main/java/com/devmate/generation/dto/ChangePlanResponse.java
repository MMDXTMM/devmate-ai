package com.devmate.generation.dto;
import com.devmate.generation.model.ProjectChangePlan;
public record ChangePlanResponse(String id,String revisionId,String request,String status,ProjectChangePlan plan,
        String resultRevisionId,String errorMessage) { }
