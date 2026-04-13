package com.livelinter.server;

import com.livelinter.analysis.AnalysisService;
import com.livelinter.analysis.AnalysisSnapshot;
import com.livelinter.diagnostics.DiagnosticPublisher;
import com.livelinter.diagnostics.LintDiagnostic;
import com.livelinter.documents.DocumentStore;
import com.livelinter.documents.DocumentUpdateResult;
import com.livelinter.documents.TextDocumentSnapshot;
import com.livelinter.documents.TextSpan;
import com.livelinter.parser.ParseResult;
import com.livelinter.parser.ParseService;
import com.livelinter.rules.RuleContext;
import com.livelinter.rules.RuleEngine;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class LiveLinterEngine {
    private final DocumentStore documentStore;
    private final ParseService parseService;
    private final AnalysisService analysisService;
    private final RuleEngine ruleEngine;
    private final DiagnosticPublisher diagnosticPublisher;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "live-linter-analysis");
        thread.setDaemon(true);
        return thread;
    });

    public LiveLinterEngine(
            DocumentStore documentStore,
            ParseService parseService,
            AnalysisService analysisService,
            RuleEngine ruleEngine,
            DiagnosticPublisher diagnosticPublisher) {
        this.documentStore = documentStore;
        this.parseService = parseService;
        this.analysisService = analysisService;
        this.ruleEngine = ruleEngine;
        this.diagnosticPublisher = diagnosticPublisher;
    }

    public void open(URI uri, String text, int version) {
        TextDocumentSnapshot snapshot = documentStore.didOpen(uri, text, version);
        scheduleAnalysis(snapshot.uri(), snapshot.version(), new TextSpan(0, snapshot.text().length()));
    }

    public void change(URI uri, List<org.eclipse.lsp4j.TextDocumentContentChangeEvent> changes, int version) {
        DocumentUpdateResult updateResult = documentStore.didChange(uri, changes, version);
        if (!updateResult.stale()) {
            scheduleAnalysis(uri, updateResult.snapshot().version(), updateResult.changedSpan());
        }
    }

    public void save(URI uri) {
        documentStore.get(uri).ifPresent(snapshot -> scheduleAnalysis(uri, snapshot.version(), new TextSpan(0, snapshot.text().length())));
    }

    public void close(URI uri) {
        diagnosticPublisher.clear(uri);
        documentStore.didClose(uri);
    }

    public CompletableFuture<Hover> hover(URI uri, Position position) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<TextDocumentSnapshot> snapshot = documentStore.get(uri);
            if (snapshot.isEmpty()) {
                return null;
            }
            int offset = snapshot.orElseThrow().lineIndex().offsetAt(position);
            List<LintDiagnostic> diagnostics = documentStore.getLatestDiagnostics(uri).stream()
                    .filter(diagnostic -> diagnostic.span().contains(offset))
                    .toList();
            if (diagnostics.isEmpty()) {
                return null;
            }
            String message = diagnostics.stream().map(LintDiagnostic::hoverMessage).distinct().reduce((left, right) -> left + "\n\n---\n\n" + right).orElse("");
            Hover hover = new Hover();
            MarkupContent content = new MarkupContent();
            content.setKind("markdown");
            content.setValue(message);
            hover.setContents(Either.forRight(content));
            return hover;
        }, executor);
    }

    public CompletableFuture<List<Either<Command, CodeAction>>> codeActions(URI uri, CodeActionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<TextDocumentSnapshot> snapshot = documentStore.get(uri);
            if (snapshot.isEmpty()) {
                return List.of();
            }
            TextSpan requestedSpan = snapshot.orElseThrow().lineIndex().spanOf(params.getRange());
            VersionedTextDocumentIdentifier identifier = new VersionedTextDocumentIdentifier(uri.toString(), snapshot.orElseThrow().version());

            List<Either<Command, CodeAction>> actions = new ArrayList<>();
            for (LintDiagnostic diagnostic : documentStore.getLatestDiagnostics(uri)) {
                if (!diagnostic.span().overlaps(requestedSpan) || diagnostic.fixTitle().isEmpty() || diagnostic.replacementText().isEmpty()) {
                    continue;
                }
                CodeAction action = new CodeAction(diagnostic.fixTitle().orElseThrow());
                action.setKind(CodeActionKind.QuickFix);
                action.setDiagnostics(List.of());
                TextEdit edit = new TextEdit(snapshot.orElseThrow().lineIndex().rangeOf(diagnostic.span()), diagnostic.replacementText().orElseThrow());
                WorkspaceEdit workspaceEdit = new WorkspaceEdit();
                workspaceEdit.setDocumentChanges(List.of(Either.forLeft(new org.eclipse.lsp4j.TextDocumentEdit(identifier, List.of(edit)))));
                action.setEdit(workspaceEdit);
                actions.add(Either.forRight(action));
            }
            return actions;
        }, executor);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void scheduleAnalysis(URI uri, int version, TextSpan changedSpan) {
        executor.submit(() -> analyzeIfCurrent(uri, version, changedSpan));
    }

    private void analyzeIfCurrent(URI uri, int version, TextSpan changedSpan) {
        Optional<TextDocumentSnapshot> snapshotOptional = documentStore.get(uri);
        if (snapshotOptional.isEmpty()) {
            return;
        }
        TextDocumentSnapshot snapshot = snapshotOptional.orElseThrow();
        if (snapshot.version() != version) {
            return;
        }

        long start = System.nanoTime();
        ParseResult parseResult = parseService.parse(snapshot, changedSpan);
        if (documentStore.currentVersion(uri) != version) {
            return;
        }
        documentStore.updateParseResult(uri, parseResult);

        AnalysisSnapshot analysisSnapshot = analysisService.analyze(snapshot, parseResult);
        if (documentStore.currentVersion(uri) != version) {
            return;
        }

        List<LintDiagnostic> diagnostics = new ArrayList<>(parseResult.syntaxDiagnostics());
        diagnostics.addAll(ruleEngine.evaluate(new RuleContext(snapshot, parseResult, analysisSnapshot)));
        documentStore.updateDiagnostics(uri, diagnostics);
        diagnosticPublisher.publishIfCurrent(uri, version, diagnostics);

        long totalMillis = (System.nanoTime() - start) / 1_000_000;
        System.err.printf(
                "[live-linter] %s v%d parsed in %d ms, analyzed in %d ms, total %d ms, diagnostics=%d%n",
                uri,
                version,
                parseResult.durationMillis(),
                analysisSnapshot.durationMillis(),
                totalMillis,
                diagnostics.size());
    }
}
