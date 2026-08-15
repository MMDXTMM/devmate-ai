package com.devmate.generation.model;

public record RequirementOption(
        String id,
        String label,
        String description,
        String impact,
        boolean recommended
) {
}
