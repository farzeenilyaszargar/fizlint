package com.livelinter.parser;

import com.livelinter.documents.TextSpan;

public record AnalysisRegion(TextSpan span, String label) {
    public static AnalysisRegion wholeDocument(int length) {
        return new AnalysisRegion(new TextSpan(0, Math.max(0, length)), "document");
    }
}
