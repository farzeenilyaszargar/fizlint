package com.livelinter.server;

import com.livelinter.analysis.AnalysisService;
import com.livelinter.diagnostics.DiagnosticPublisher;
import com.livelinter.documents.DocumentStore;
import com.livelinter.lsp.LiveTextDocumentService;
import com.livelinter.lsp.LiveWorkspaceService;
import com.livelinter.parser.ParseService;
import com.livelinter.rules.DefaultRules;
import com.livelinter.rules.RuleEngine;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public class LiveLinterServer implements LanguageServer, LanguageClientAware {
    private final LiveTextDocumentService textDocumentService;
    private final LiveWorkspaceService workspaceService;
    private final LiveLinterEngine engine;
    private final DiagnosticPublisher diagnosticPublisher;

    public LiveLinterServer() {
        DocumentStore documentStore = new DocumentStore();
        this.diagnosticPublisher = new DiagnosticPublisher(documentStore);
        this.engine = new LiveLinterEngine(
                documentStore,
                new ParseService(),
                new AnalysisService(),
                new RuleEngine(DefaultRules.create()),
                diagnosticPublisher);
        this.textDocumentService = new LiveTextDocumentService(engine);
        this.workspaceService = new LiveWorkspaceService();
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental);
        capabilities.setHoverProvider(Either.forLeft(Boolean.TRUE));
        capabilities.setCodeActionProvider(Either.forLeft(Boolean.TRUE));
        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        engine.shutdown();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        engine.shutdown();
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public void connect(LanguageClient client) {
        diagnosticPublisher.connect(client);
    }
}
