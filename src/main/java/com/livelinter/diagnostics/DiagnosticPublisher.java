package com.livelinter.diagnostics;

import com.livelinter.documents.DocumentStore;
import com.livelinter.documents.LineIndex;
import com.livelinter.documents.TextDocumentSnapshot;
import java.net.URI;
import java.util.List;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;

public class DiagnosticPublisher {
    private final DocumentStore documentStore;
    private volatile LanguageClient client;

    public DiagnosticPublisher(DocumentStore documentStore) {
        this.documentStore = documentStore;
    }

    public void connect(LanguageClient client) {
        this.client = client;
    }

    public void publishIfCurrent(URI uri, int version, List<LintDiagnostic> diagnostics) {
        if (documentStore.currentVersion(uri) != version) {
            return;
        }
        documentStore.get(uri).ifPresent(snapshot -> publish(snapshot, diagnostics));
    }

    public void clear(URI uri) {
        LanguageClient currentClient = client;
        if (currentClient == null) {
            return;
        }
        PublishDiagnosticsParams params = new PublishDiagnosticsParams(uri.toString(), List.of());
        currentClient.publishDiagnostics(params);
    }

    private void publish(TextDocumentSnapshot snapshot, List<LintDiagnostic> diagnostics) {
        LanguageClient currentClient = client;
        if (currentClient == null) {
            return;
        }

        LineIndex lineIndex = snapshot.lineIndex();
        List<Diagnostic> lspDiagnostics = diagnostics.stream().map(diagnostic -> {
            Diagnostic lspDiagnostic = new Diagnostic();
            lspDiagnostic.setRange(lineIndex.rangeOf(diagnostic.span()));
            lspDiagnostic.setSeverity(diagnostic.severity().toLspSeverity());
            lspDiagnostic.setMessage(diagnostic.message());
            lspDiagnostic.setSource(diagnostic.source());
            lspDiagnostic.setCode(Either.forLeft(diagnostic.code()));
            return lspDiagnostic;
        }).toList();

        PublishDiagnosticsParams params = new PublishDiagnosticsParams(snapshot.uri().toString(), lspDiagnostics);
        params.setVersion(snapshot.version());
        currentClient.publishDiagnostics(params);
    }
}
