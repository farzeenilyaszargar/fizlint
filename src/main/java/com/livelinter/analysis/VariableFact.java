package com.livelinter.analysis;

import com.livelinter.documents.TextSpan;

public record VariableFact(String name, TextSpan span, boolean parameter, boolean used) {
}
