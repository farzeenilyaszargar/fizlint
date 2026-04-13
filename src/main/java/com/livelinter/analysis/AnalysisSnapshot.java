package com.livelinter.analysis;

import com.livelinter.parser.AnalysisRegion;
import java.util.List;

public record AnalysisSnapshot(
        AnalysisRegion region,
        List<VariableFact> variableFacts,
        List<UnreachableCodeFact> unreachableCodeFacts,
        List<LogicalPatternFact> logicalPatternFacts,
        long durationMillis) {
}
