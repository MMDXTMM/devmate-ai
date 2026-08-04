package com.devmate.review.source;

import com.devmate.review.model.StaticAnalysisResult;
import com.devmate.review.model.StaticAnalysisTarget;

import java.nio.file.Path;
import java.util.List;

public interface JavaStaticAnalyzer {

    String toolName();

    String toolVersion();

    StaticAnalysisResult analyze(Path repositoryRoot, List<StaticAnalysisTarget> targets);
}
