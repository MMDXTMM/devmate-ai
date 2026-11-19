package com.devmate.generation.dto;
import jakarta.validation.constraints.*;
public record InsightRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{16,80}") String attemptKey) { }
