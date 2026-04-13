package com.livelinter.analysis;

import com.livelinter.documents.TextSpan;

public record UnreachableCodeFact(TextSpan span, String reason) {
}
