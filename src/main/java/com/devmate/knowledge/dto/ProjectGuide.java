package com.devmate.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Only Content comes from the model. Snapshot, coverage and evidence locations are host-owned. */
@Schema(description = "project-guide-v1：用途、能力、流程与可按需展开的实现依据；旧报告该字段为空")
public record ProjectGuide(String schemaVersion, Snapshot snapshot, Content content,
                           Coverage coverage, List<Evidence> evidence) {
    public enum ConclusionStatus { OBSERVED, INFERRED, UNKNOWN }
    public enum ImplementationStatus { IMPLEMENTED, PARTIAL, PLANNED, UNCLEAR }
    public record Snapshot(String revision, String contentDigest, String readPolicy) { }
    public record Coverage(int indexedFiles, int readFiles, int readChunks, int readCharacters,
                           int maxChunks, int maxCharacters, List<String> limitations) { }
    public record Evidence(String id, String sourceKind, String revision, String symbolName,
                           String filePath, Integer startLine, Integer endLine, String code, boolean truncated) { }
    public record Content(ProjectInfo project, List<Module> modules, List<Flow> flows,
                          List<Term> glossary, List<String> openQuestions) { }
    public record ProjectInfo(String name, String type, String purpose, String audience, String input,
                              String output, String scenario, ConclusionStatus conclusionStatus,
                              ImplementationStatus implementationStatus, List<String> evidenceIds) { }
    public record Module(String id, String name, String responsibility, List<String> capabilities,
                         String input, String output, List<String> relatedModuleIds,
                         ConclusionStatus conclusionStatus, ImplementationStatus implementationStatus,
                         List<String> evidenceIds) { }
    public record Flow(String id, String name, String goal, List<String> actors, String trigger,
                       List<String> moduleIds, List<Step> steps, List<Claim> rules,
                       List<Claim> outcomes, List<String> unknowns) { }
    public record Step(String action, String input, String result, List<String> moduleIds,
                       ConclusionStatus conclusionStatus, List<String> evidenceIds) { }
    public record Claim(String text, ConclusionStatus conclusionStatus, List<String> evidenceIds) { }
    public record Term(String term, String explanation) { }
}
