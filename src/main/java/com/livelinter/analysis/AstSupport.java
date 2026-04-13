package com.livelinter.analysis;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.livelinter.documents.LineIndex;
import com.livelinter.documents.TextSpan;

public final class AstSupport {
    private AstSupport() {
    }

    public static TextSpan toSpan(LineIndex lineIndex, Node node, TextSpan region) {
        return node.getRange()
                .map(range -> toSpan(lineIndex, range))
                .filter(span -> region == null || span.overlaps(region))
                .orElse(null);
    }

    public static TextSpan toSpan(LineIndex lineIndex, Range range) {
        org.eclipse.lsp4j.Position start = new org.eclipse.lsp4j.Position(range.begin.line - 1, range.begin.column - 1);
        org.eclipse.lsp4j.Position end = new org.eclipse.lsp4j.Position(range.end.line - 1, range.end.column);
        return new TextSpan(lineIndex.offsetAt(start), lineIndex.offsetAt(end));
    }
}
