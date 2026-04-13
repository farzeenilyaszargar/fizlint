package com.livelinter.rules;

import com.livelinter.analysis.AnalysisSnapshot;
import com.livelinter.documents.TextDocumentSnapshot;
import com.livelinter.parser.ParseResult;

public record RuleContext(TextDocumentSnapshot snapshot, ParseResult parseResult, AnalysisSnapshot analysisSnapshot) {
}
