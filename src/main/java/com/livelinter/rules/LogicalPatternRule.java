package com.livelinter.rules;

import com.livelinter.analysis.LogicalPatternFact;
import com.livelinter.diagnostics.LintDiagnostic;
import com.livelinter.diagnostics.LintSeverity;
import java.util.ArrayList;
import java.util.List;

public class LogicalPatternRule implements Rule {
    @Override
    public String id() {
        return "logical-pattern";
    }

    @Override
    public List<LintDiagnostic> evaluate(RuleContext context) {
        List<LintDiagnostic> diagnostics = new ArrayList<>();
        for (LogicalPatternFact fact : context.analysisSnapshot().logicalPatternFacts()) {
            diagnostics.add(LintDiagnostic.builder(fact.code(), fact.message(), LintSeverity.WARNING, fact.span())
                    .source("logic")
                    .suggestion(fact.suggestion())
                    .build());
        }
        return diagnostics;
    }
}
