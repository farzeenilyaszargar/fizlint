package com.livelinter.rules;

import com.livelinter.analysis.VariableFact;
import com.livelinter.diagnostics.LintDiagnostic;
import com.livelinter.diagnostics.LintSeverity;
import java.util.ArrayList;
import java.util.List;

public class UnusedVariableRule implements Rule {
    @Override
    public String id() {
        return "unused-variable";
    }

    @Override
    public List<LintDiagnostic> evaluate(RuleContext context) {
        List<LintDiagnostic> diagnostics = new ArrayList<>();
        for (VariableFact variableFact : context.analysisSnapshot().variableFacts()) {
            if (variableFact.used()) {
                continue;
            }
            String kind = variableFact.parameter() ? "parameter" : "local variable";
            diagnostics.add(LintDiagnostic.builder(id(), "Unused " + kind + " '" + variableFact.name() + "'.", LintSeverity.WARNING, variableFact.span())
                    .source("semantic")
                    .suggestion("Remove it, or use it to make the intent of the code explicit.")
                    .build());
        }
        return diagnostics;
    }
}
