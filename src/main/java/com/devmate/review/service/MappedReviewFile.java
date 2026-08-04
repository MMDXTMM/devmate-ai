package com.devmate.review.service;

import com.devmate.review.dto.MappedSymbolResponse;
import com.devmate.review.model.GitChangedFile;

import java.util.List;

record MappedReviewFile(
        GitChangedFile changedFile,
        String coverageStatus,
        List<MappedSymbolResponse> mappedSymbols,
        String skipReason
) {
    MappedReviewFile {
        mappedSymbols = List.copyOf(mappedSymbols);
    }
}
