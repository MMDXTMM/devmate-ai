package com.devmate.review.dto;

import com.devmate.review.model.LineRange;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

public record ReviewFileResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String oldPath,
        String newPath,
        String changeType,
        String coverageStatus,
        Integer additions,
        Integer deletions,
        List<LineRange> changedLines,
        List<MappedSymbolResponse> mappedSymbols,
        String skipReason
) {
}
