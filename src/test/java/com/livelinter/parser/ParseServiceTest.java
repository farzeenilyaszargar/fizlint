package com.livelinter.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livelinter.documents.TextDocumentSnapshot;
import com.livelinter.documents.TextSpan;
import java.net.URI;
import org.junit.jupiter.api.Test;

class ParseServiceTest {
    private final ParseService parseService = new ParseService();

    @Test
    void reportsSyntaxProblemsForIncompleteJava() {
        TextDocumentSnapshot snapshot = new TextDocumentSnapshot(
                URI.create("file:///Broken.java"),
                "class Broken { void test( }",
                1);

        ParseResult result = parseService.parse(snapshot, new TextSpan(0, snapshot.text().length()));

        assertFalse(result.syntaxDiagnostics().isEmpty());
    }

    @Test
    void selectsSmallestAnalysisRegionContainingTheEdit() {
        String text = "class Demo {\n  void a() { int x = 0; }\n  void b() { int y = 0; }\n}\n";
        TextDocumentSnapshot snapshot = new TextDocumentSnapshot(URI.create("file:///Demo.java"), text, 1);
        int start = text.indexOf("y = 0");
        int end = start + 1;

        ParseResult result = parseService.parse(snapshot, new TextSpan(start, end));

        assertTrue(result.compilationUnit().isPresent());
        assertFalse("document".equals(result.analysisRegion().label()));
    }
}
