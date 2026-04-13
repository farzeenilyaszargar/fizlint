package com.livelinter.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.Range;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.livelinter.diagnostics.LintDiagnostic;
import com.livelinter.diagnostics.LintSeverity;
import com.livelinter.documents.LineIndex;
import com.livelinter.documents.TextDocumentSnapshot;
import com.livelinter.documents.TextSpan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ParseService {
    private final JavaParser parser;

    public ParseService() {
        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setAttributeComments(false);
        configuration.setStoreTokens(true);
        this.parser = new JavaParser(configuration);
    }

    public com.livelinter.parser.ParseResult parse(TextDocumentSnapshot snapshot, TextSpan changedSpan) {
        long start = System.nanoTime();
        List<LintDiagnostic> syntaxDiagnostics = new ArrayList<>();
        Optional<CompilationUnit> compilationUnit = Optional.empty();

        try {
            com.github.javaparser.ParseResult<CompilationUnit> parseResult = parser.parse(snapshot.text());
            compilationUnit = parseResult.getResult();
            for (Problem problem : parseResult.getProblems()) {
                syntaxDiagnostics.add(problemToDiagnostic(snapshot, problem));
            }
        } catch (ParseProblemException ex) {
            for (Problem problem : ex.getProblems()) {
                syntaxDiagnostics.add(problemToDiagnostic(snapshot, problem));
            }
        }

        AnalysisRegion analysisRegion = compilationUnit
                .map(unit -> determineRegion(snapshot.text().length(), snapshot.lineIndex(), unit, changedSpan))
                .orElse(AnalysisRegion.wholeDocument(snapshot.text().length()));
        long durationMillis = (System.nanoTime() - start) / 1_000_000;
        return new com.livelinter.parser.ParseResult(compilationUnit, syntaxDiagnostics, analysisRegion, durationMillis);
    }

    private LintDiagnostic problemToDiagnostic(TextDocumentSnapshot snapshot, Problem problem) {
        TextSpan span = problem.getLocation()
                .flatMap(TokenRange::toRange)
                .map(range -> toTextSpan(snapshot.lineIndex(), range))
                .orElseGet(() -> fallbackSpan(snapshot));
        return LintDiagnostic.builder("syntax", problem.getVerboseMessage(), LintSeverity.ERROR, span)
                .source("parser")
                .build();
    }

    private AnalysisRegion determineRegion(int documentLength, LineIndex lineIndex, CompilationUnit unit, TextSpan changedSpan) {
        if (changedSpan == null) {
            return AnalysisRegion.wholeDocument(documentLength);
        }

        List<Node> candidates = unit.findAll(Node.class, node -> node.getRange().isPresent()
                && isRegionCarrier(node)
                && contains(lineIndex, node.getRange().orElseThrow(), changedSpan));

        if (candidates.isEmpty()) {
            return AnalysisRegion.wholeDocument(documentLength);
        }

        Node best = candidates.stream()
                .min(Comparator.comparingInt(node -> nodeSpan(lineIndex, node).length()))
                .orElse(unit);
        TextSpan span = nodeSpan(lineIndex, best);
        return new AnalysisRegion(span, best.getClass().getSimpleName());
    }

    private boolean isRegionCarrier(Node node) {
        return node instanceof BlockStmt
                || node instanceof MethodDeclaration
                || node instanceof ConstructorDeclaration
                || node instanceof InitializerDeclaration
                || node instanceof ClassOrInterfaceDeclaration
                || node instanceof CompilationUnit;
    }

    private boolean contains(LineIndex lineIndex, Range range, TextSpan changedSpan) {
        TextSpan span = toTextSpan(lineIndex, range);
        return span.startOffset() <= changedSpan.startOffset() && span.endOffset() >= changedSpan.endOffset();
    }

    private TextSpan nodeSpan(LineIndex lineIndex, Node node) {
        return node.getRange().map(range -> toTextSpan(lineIndex, range)).orElse(TextSpan.emptyAt(0));
    }

    private TextSpan toTextSpan(LineIndex lineIndex, Range range) {
        org.eclipse.lsp4j.Position start = new org.eclipse.lsp4j.Position(range.begin.line - 1, range.begin.column - 1);
        org.eclipse.lsp4j.Position end = new org.eclipse.lsp4j.Position(range.end.line - 1, range.end.column);
        return new TextSpan(lineIndex.offsetAt(start), lineIndex.offsetAt(end));
    }

    private TextSpan fallbackSpan(TextDocumentSnapshot snapshot) {
        int endOffset = Math.min(snapshot.text().length(), 1);
        return new TextSpan(0, endOffset);
    }
}
