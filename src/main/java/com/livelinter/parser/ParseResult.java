package com.livelinter.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.livelinter.diagnostics.LintDiagnostic;
import java.util.List;
import java.util.Optional;

public record ParseResult(
        Optional<CompilationUnit> compilationUnit,
        List<LintDiagnostic> syntaxDiagnostics,
        AnalysisRegion analysisRegion,
        long durationMillis) {
}
