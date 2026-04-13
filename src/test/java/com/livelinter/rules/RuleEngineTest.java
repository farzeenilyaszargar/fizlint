package com.livelinter.rules;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livelinter.analysis.AnalysisService;
import com.livelinter.parser.ParseResult;
import com.livelinter.parser.ParseService;
import com.livelinter.documents.TextDocumentSnapshot;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleEngineTest {
    private final ParseService parseService = new ParseService();
    private final AnalysisService analysisService = new AnalysisService();
    private final RuleEngine ruleEngine = new RuleEngine(DefaultRules.create());

    @Test
    void emitsStyleAndSemanticDiagnostics() {
        String source = "class Demo {\n"
                + "  void sample(int unusedParam) {    \n"
                + "    int unused = 1;\n"
                + "    if (true) {}\n"
                + "    return;\n"
                + "    int afterReturn = 2;\n"
                + "  }\n"
                + "}\n";
        TextDocumentSnapshot snapshot = new TextDocumentSnapshot(URI.create("file:///Demo.java"), source, 1);
        ParseResult parseResult = parseService.parse(snapshot, null);

        List<String> codes = ruleEngine.evaluate(new RuleContext(snapshot, parseResult, analysisService.analyze(snapshot, parseResult)))
                .stream()
                .map(diagnostic -> diagnostic.code())
                .toList();

        assertTrue(codes.contains("style-trailing-whitespace"));
        assertTrue(codes.contains("unused-variable"));
        assertTrue(codes.contains("unreachable-code"));
        assertTrue(codes.contains("logical-constant-if"));
    }
}
