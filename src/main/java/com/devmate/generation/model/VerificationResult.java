package com.devmate.generation.model;
/** Only runner evidence, never a model's claim of passing tests. */
public record VerificationResult(String status, String profile, String command, Integer exitCode,
                                 String output, long durationMs, boolean testsExecuted) { }
