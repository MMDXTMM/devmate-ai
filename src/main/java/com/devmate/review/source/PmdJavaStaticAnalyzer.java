package com.devmate.review.source;

import com.devmate.knowledge.source.SourceImportException;
import com.devmate.review.model.LineRange;
import com.devmate.review.model.StaticAnalysisResult;
import com.devmate.review.model.StaticAnalysisTarget;
import com.devmate.review.model.StaticFinding;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.rule.RulePriority;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PmdJavaStaticAnalyzer implements JavaStaticAnalyzer {

    private static final String TOOL_NAME = "PMD";
    private static final String RULESET = "rulesets/devmate-java.xml";
    private static final Map<String, String> CATEGORIES = Map.of(
            "CloseResource", "RESOURCE",
            "EmptyCatchBlock", "ERROR_HANDLING",
            "ReturnEmptyCollectionRatherThanNull", "API_DESIGN",
            "AvoidPrintStackTrace", "LOGGING",
            "UnusedLocalVariable", "CODE_QUALITY"
    );

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String toolVersion() {
        String version = PmdAnalysis.class.getPackage().getImplementationVersion();
        return version == null ? "7.26.0" : version;
    }

    @Override
    public StaticAnalysisResult analyze(Path repositoryRoot, List<StaticAnalysisTarget> targets) {
        if (targets.isEmpty()) {
            return new StaticAnalysisResult(toolName(), toolVersion(), 0, List.of());
        }

        PMDConfiguration configuration = new PMDConfiguration();
        Language java = LanguageRegistry.PMD.getLanguageById("java");
        configuration.setDefaultLanguageVersion(java.getVersion("21"));
        configuration.setSourceEncoding(StandardCharsets.UTF_8);
        configuration.setThreads(1);
        configuration.addRelativizeRoot(repositoryRoot);

        Map<Path, StaticAnalysisTarget> byAbsolutePath = new HashMap<>();
        try (PmdAnalysis analysis = PmdAnalysis.create(configuration)) {
            analysis.addRuleSet(analysis.newRuleSetLoader().loadFromResource(RULESET));
            for (StaticAnalysisTarget target : targets) {
                byAbsolutePath.put(target.sourcePath(), target);
                if (!analysis.files().addFile(target.sourcePath(), java)) {
                    throw new SourceImportException("PMD无法识别Java文件：" + target.relativePath());
                }
            }
            Report report = analysis.performAnalysisAndCollectReport();
            if (!report.getConfigurationErrors().isEmpty()) {
                throw new SourceImportException("PMD规则配置无效：" + report.getConfigurationErrors().getFirst().issue());
            }
            if (!report.getProcessingErrors().isEmpty()) {
                Report.ProcessingError error = report.getProcessingErrors().getFirst();
                throw new SourceImportException("PMD解析源码失败：" + error.getFileId().getFileName());
            }

            List<StaticFinding> findings = new ArrayList<>();
            for (RuleViolation violation : report.getViolations()) {
                StaticAnalysisTarget target = resolveTarget(violation, byAbsolutePath);
                if (target != null && intersectsChangedLines(violation, target.changedLines())) {
                    findings.add(toFinding(violation, target.relativePath()));
                }
            }
            return new StaticAnalysisResult(toolName(), toolVersion(), targets.size(), findings);
        } catch (SourceImportException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SourceImportException("PMD静态分析执行失败", exception);
        }
    }

    private StaticAnalysisTarget resolveTarget(
            RuleViolation violation,
            Map<Path, StaticAnalysisTarget> targets
    ) {
        String absolutePath = violation.getFileId().getAbsolutePath();
        if (absolutePath == null) {
            return null;
        }
        return targets.get(Path.of(absolutePath).toAbsolutePath().normalize());
    }

    private boolean intersectsChangedLines(RuleViolation violation, List<LineRange> changedLines) {
        for (LineRange range : changedLines) {
            if (violation.getBeginLine() <= range.endLine() && violation.getEndLine() >= range.startLine()) {
                return true;
            }
        }
        return false;
    }

    private StaticFinding toFinding(RuleViolation violation, String relativePath) {
        String ruleId = violation.getRule().getName();
        return new StaticFinding(
                ruleId,
                CATEGORIES.getOrDefault(ruleId, "CODE_QUALITY"),
                severity(violation.getRule().getPriority()),
                relativePath,
                violation.getBeginLine(),
                violation.getEndLine(),
                violation.getDescription(),
                TOOL_NAME + "规则 " + ruleId + " 在目标版本源码中给出确定性违规"
        );
    }

    private String severity(RulePriority priority) {
        return switch (priority) {
            case HIGH, MEDIUM_HIGH -> "HIGH";
            case MEDIUM -> "MEDIUM";
            case MEDIUM_LOW, LOW -> "LOW";
        };
    }

}
