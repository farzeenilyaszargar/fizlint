package com.livelinter.rules;

import com.livelinter.diagnostics.LintDiagnostic;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RuleEngine {
    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<LintDiagnostic> evaluate(RuleContext context) {
        List<LintDiagnostic> diagnostics = new ArrayList<>();
        for (Rule rule : rules) {
            diagnostics.addAll(rule.evaluate(context));
        }
        diagnostics.sort(Comparator.comparingInt((LintDiagnostic diagnostic) -> diagnostic.span().startOffset())
                .thenComparing(diagnostic -> diagnostic.code()));
        return diagnostics;
    }
}
