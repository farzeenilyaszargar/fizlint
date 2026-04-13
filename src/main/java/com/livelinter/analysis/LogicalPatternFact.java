package com.livelinter.analysis;

import com.livelinter.documents.TextSpan;

public record LogicalPatternFact(String code, TextSpan span, String message, String suggestion) {
}
