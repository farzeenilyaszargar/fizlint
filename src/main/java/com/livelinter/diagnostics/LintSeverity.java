package com.livelinter.diagnostics;

import org.eclipse.lsp4j.DiagnosticSeverity;

public enum LintSeverity {
    ERROR(DiagnosticSeverity.Error),
    WARNING(DiagnosticSeverity.Warning),
    INFORMATION(DiagnosticSeverity.Information),
    HINT(DiagnosticSeverity.Hint);

    private final DiagnosticSeverity lspSeverity;

    LintSeverity(DiagnosticSeverity lspSeverity) {
        this.lspSeverity = lspSeverity;
    }

    public DiagnosticSeverity toLspSeverity() {
        return lspSeverity;
    }
}
