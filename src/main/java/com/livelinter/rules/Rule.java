package com.livelinter.rules;

import com.livelinter.diagnostics.LintDiagnostic;
import java.util.List;

public interface Rule {
    String id();

    List<LintDiagnostic> evaluate(RuleContext context);
}
