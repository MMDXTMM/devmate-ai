package com.devmate.knowledge.service;

record SourceImportMetrics(
        long cloneDurationMs,
        long scanDurationMs,
        long planDurationMs,
        long parseDurationMs,
        long totalStartedNanos
) {

    static SourceImportMetrics empty(long totalStartedNanos) {
        return new SourceImportMetrics(0, 0, 0, 0, totalStartedNanos);
    }

    SourceImportMetrics withCloneDuration(long durationMs) {
        return new SourceImportMetrics(
                durationMs, scanDurationMs, planDurationMs, parseDurationMs, totalStartedNanos
        );
    }

    SourceImportMetrics withScanDuration(long durationMs) {
        return new SourceImportMetrics(
                cloneDurationMs, durationMs, planDurationMs, parseDurationMs, totalStartedNanos
        );
    }

    SourceImportMetrics withPlanDuration(long durationMs) {
        return new SourceImportMetrics(
                cloneDurationMs, scanDurationMs, durationMs, parseDurationMs, totalStartedNanos
        );
    }

    SourceImportMetrics withParseDuration(long durationMs) {
        return new SourceImportMetrics(
                cloneDurationMs, scanDurationMs, planDurationMs, durationMs, totalStartedNanos
        );
    }

    long totalDurationMs() {
        return elapsedMillis(totalStartedNanos);
    }

    static long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }
}
