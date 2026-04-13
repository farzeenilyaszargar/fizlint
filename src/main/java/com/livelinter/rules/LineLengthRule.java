package com.livelinter.rules;

import com.livelinter.diagnostics.LintDiagnostic;
import com.livelinter.diagnostics.LintSeverity;
import com.livelinter.documents.LineIndex;
import com.livelinter.documents.TextSpan;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.lsp4j.Position;

public class LineLengthRule implements Rule {
    private final int maxLineLength;

    public LineLengthRule(int maxLineLength) {
        this.maxLineLength = maxLineLength;
    }

    @Override
    public String id() {
        return "style-line-length";
    }

    @Override
    public List<LintDiagnostic> evaluate(RuleContext context) {
        LineIndex lineIndex = context.snapshot().lineIndex();
        List<LintDiagnostic> diagnostics = new ArrayList<>();
        for (int line = 0; line < lineIndex.lineCount(); line++) {
            String lineText = lineIndex.lineText(line);
            if (lineText.length() <= maxLineLength) {
                continue;
            }
            int start = lineIndex.offsetAt(new Position(line, maxLineLength));
            int end = lineIndex.offsetAt(new Position(line, lineText.length()));
            diagnostics.add(LintDiagnostic.builder(id(), "Line exceeds " + maxLineLength + " characters.", LintSeverity.INFORMATION, new TextSpan(start, end))
                    .source("style")
                    .suggestion("Consider wrapping the expression or extracting part of it into a named variable.")
                    .build());
        }
        return diagnostics;
    }
}
