package com.devmate.generation.dto;
import jakarta.validation.constraints.*;
public record VerificationRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{16,80}") String attemptKey,
                                  @Min(0) @Max(2) int repairLimit) { }
