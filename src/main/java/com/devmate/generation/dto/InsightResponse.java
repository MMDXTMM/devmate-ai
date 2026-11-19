package com.devmate.generation.dto;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.JsonNode;
public record InsightResponse(String id,String revisionId,String kind,String status,JsonNode content,String errorMessage,LocalDateTime createdAt,LocalDateTime updatedAt) { }
