package com.livelinter.rules;

import com.livelinter.diagnostics.LintDiagnostic;
import com.livelinter.diagnostics.LintSeverity;
import com.livelinter.documents.LineIndex;
import com.livelinter.documents.TextSpan;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.lsp4j.Position;

public class TrailingWhitespaceRule implements Rule {
    @Override
    public String id() {
        return "style-trailing-whitespace";
    }

    @Override
    public List<LintDiagnostic> evaluate(RuleContext context) {
        LineIndex lineIndex = context.snapshot().lineIndex();
        List<LintDiagnostic> diagnostics = new ArrayList<>();
        for (int line = 0; line < lineIndex.lineCount(); line++) {
            String lineText = lineIndex.lineText(line);
            int trimmedLength = trimRightLength(lineText);
            if (trimmedLength == lineText.length()) {
                continue;
            }
            int start = lineIndex.offsetAt(new Position(line, trimmedLength));
            int end = lineIndex.offsetAt(new Position(line, lineText.length()));
            diagnostics.add(LintDiagnostic.builder(id(), "Line has trailing whitespace.", LintSeverity.INFORMATION, new TextSpan(start, end))
                    .source("style")
                    .suggestion("Remove trailing spaces to keep diffs and formatting clean.")
                    .quickFix("Remove trailing whitespace", "")
                    .build());
        }
        return diagnostics;
    }

    private int trimRightLength(String value) {
        int index = value.length();
        while (index > 0) {
            char ch = value.charAt(index - 1);
            if (ch == ' ' || ch == '\t') {
                index--;
            } else {
                break;
            }
        }
        return index;
    }
}
