package com.livelinter.rules;

import com.livelinter.analysis.UnreachableCodeFact;
import com.livelinter.diagnostics.LintDiagnostic;
import com.livelinter.diagnostics.LintSeverity;
import java.util.ArrayList;
import java.util.List;

public class UnreachableCodeRule implements Rule {
    @Override
    public String id() {
        return "unreachable-code";
    }

    @Override
    public List<LintDiagnostic> evaluate(RuleContext context) {
        List<LintDiagnostic> diagnostics = new ArrayList<>();
        for (UnreachableCodeFact fact : context.analysisSnapshot().unreachableCodeFacts()) {
            diagnostics.add(LintDiagnostic.builder(id(), "Unreachable statement.", LintSeverity.WARNING, fact.span())
                    .source("flow")
                    .suggestion(fact.reason())
                    .build());
        }
        return diagnostics;
    }
}
