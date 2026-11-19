package com.devmate.generation.dto;
import com.devmate.generation.model.VerificationResult;
import java.time.LocalDateTime;
public record VerificationResponse(String id, String revisionId, String status, int repairs, int repairLimit,
        String resultRevisionId, VerificationResult result, String errorMessage, LocalDateTime createdAt, LocalDateTime updatedAt) { }
