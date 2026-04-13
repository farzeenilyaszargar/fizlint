package com.livelinter.lsp;

import com.livelinter.server.LiveLinterEngine;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

public class LiveTextDocumentService implements TextDocumentService {
    private final LiveLinterEngine engine;

    public LiveTextDocumentService(LiveLinterEngine engine) {
        this.engine = engine;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        TextDocumentItem document = params.getTextDocument();
        engine.open(URI.create(document.getUri()), document.getText(), document.getVersion());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        VersionedTextDocumentIdentifier document = params.getTextDocument();
        engine.change(URI.create(document.getUri()), params.getContentChanges(), document.getVersion());
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        engine.close(URI.create(params.getTextDocument().getUri()));
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        engine.save(URI.create(params.getTextDocument().getUri()));
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        return engine.hover(URI.create(params.getTextDocument().getUri()), params.getPosition());
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        return engine.codeActions(URI.create(params.getTextDocument().getUri()), params);
    }
}
