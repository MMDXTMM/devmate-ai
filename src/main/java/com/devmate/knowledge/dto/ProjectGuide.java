package com.devmate.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Only Content comes from the model. Snapshot, coverage and evidence locations are host-owned. */
@Schema(description = "project-guide-v1：用途、能力、流程与可按需展开的实现依据；旧报告该字段为空")
public record ProjectGuide(String schemaVersion, Snapshot snapshot, Content content,
                           Coverage coverage, List<Evidence> evidence, List<String> languages) {
    public ProjectGuide(String schemaVersion, Snapshot snapshot, Content content, Coverage coverage, List<Evidence> evidence) {
        this(schemaVersion, snapshot, content, coverage, evidence, List.of());
    }
    public enum ConclusionStatus { OBSERVED, INFERRED, UNKNOWN }
    public enum ImplementationStatus { IMPLEMENTED, PARTIAL, PLANNED, UNCLEAR }
    public record Snapshot(String revision, String contentDigest, String readPolicy) { }
    public record Coverage(int indexedFiles, int readFiles, int readChunks, int readCharacters,
                           int maxChunks, int maxCharacters, List<String> limitations) { }
    public record Evidence(String id, String sourceKind, String revision, String symbolName,
                           String filePath, Integer startLine, Integer endLine, String code, boolean truncated) { }
    public record Content(ProjectInfo project, List<Module> modules, List<Flow> flows,
                          List<Term> glossary, List<String> openQuestions, Overview overview,
                          Appearance appearance) {
        public Content(ProjectInfo project, List<Module> modules, List<Flow> flows,
                       List<Term> glossary, List<String> openQuestions, Overview overview) {
            this(project, modules, flows, glossary, openQuestions, overview, null);
        }
        public Content(ProjectInfo project, List<Module> modules, List<Flow> flows,
                       List<Term> glossary, List<String> openQuestions) {
            this(project, modules, flows, glossary, openQuestions, null, null);
        }
    }
    @Schema(description = "AI 根据已读项目用途及模块职责选择的装饰图标；旧报告可为空，不代表实现验证")
    public record Appearance(
            @Schema(description = "单个 Unicode emoji，不接受图片地址、HTML 或说明文本") String projectIcon,
            @Schema(description = "逐一给出本报告模块的 ID 和对应图标，最多 20 项") List<ModuleIcon> modules) {
        // Read the first experimental map representation without rewriting saved reports.
        @JsonCreator
        public static Appearance read(@JsonProperty("projectIcon") String projectIcon,
                                      @JsonProperty("modules") List<ModuleIcon> modules,
                                      @JsonProperty("moduleIcons") Map<String, String> legacyIcons) {
            if (modules == null && legacyIcons != null) {
                modules = legacyIcons.entrySet().stream().limit(20)
                        .map(entry -> new ModuleIcon(entry.getKey(), entry.getValue())).toList();
            }
            return new Appearance(projectIcon, modules);
        }
    }
    public record ModuleIcon(@Schema(description = "本报告 modules 中的模块 ID") String moduleId,
                             @Schema(description = "代表该模块职责的单个 Unicode emoji") String icon) { }
    @Schema(description = "新人学习概要；旧导览可为空，亮点来自实际实现，学习价值属于建议而非运行验证")
    public record Overview(List<Highlight> highlights) { }
    public record Highlight(String title, List<String> moduleIds, String problem, String solution,
                            String learningValue, String suggestedExercise, String limitation,
                            ConclusionStatus conclusionStatus, ImplementationStatus implementationStatus,
                            List<String> evidenceIds) { }
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
