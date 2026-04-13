package com.livelinter.documents;

import com.livelinter.diagnostics.LintDiagnostic;
import com.livelinter.parser.ParseResult;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;

public class DocumentStore {
    private final Map<URI, ManagedDocument> documents = new ConcurrentHashMap<>();

    public TextDocumentSnapshot didOpen(URI uri, String text, int version) {
        TextDocumentSnapshot snapshot = new TextDocumentSnapshot(uri, text, version);
        documents.put(uri, new ManagedDocument(snapshot));
        return snapshot;
    }

    public DocumentUpdateResult didChange(URI uri, List<TextDocumentContentChangeEvent> changes, int version) {
        ManagedDocument managedDocument = requireDocument(uri);
        synchronized (managedDocument) {
            TextDocumentSnapshot current = managedDocument.snapshot;
            if (version < current.version()) {
                return new DocumentUpdateResult(current, TextSpan.emptyAt(0), true);
            }

            String updatedText = current.text();
            LineIndex currentIndex = current.lineIndex();
            TextSpan combinedSpan = null;

            for (TextDocumentContentChangeEvent change : changes) {
                EditApplicationResult application = applyEdit(updatedText, currentIndex, change);
                updatedText = application.updatedText();
                currentIndex = new LineIndex(updatedText);
                combinedSpan = combinedSpan == null ? application.changedSpan() : combinedSpan.union(application.changedSpan());
            }

            if (combinedSpan == null) {
                combinedSpan = new TextSpan(0, updatedText.length());
            }

            TextDocumentSnapshot snapshot = new TextDocumentSnapshot(uri, updatedText, version);
            managedDocument.snapshot = snapshot;
            return new DocumentUpdateResult(snapshot, combinedSpan, false);
        }
    }

    public void didClose(URI uri) {
        documents.remove(uri);
    }

    public Optional<TextDocumentSnapshot> get(URI uri) {
        ManagedDocument document = documents.get(uri);
        return document == null ? Optional.empty() : Optional.of(document.snapshot);
    }

    public int currentVersion(URI uri) {
        ManagedDocument document = documents.get(uri);
        return document == null ? -1 : document.snapshot.version();
    }

    public void updateParseResult(URI uri, ParseResult parseResult) {
        ManagedDocument document = requireDocument(uri);
        synchronized (document) {
            document.parseResult = parseResult;
        }
    }

    public Optional<ParseResult> getLatestParseResult(URI uri) {
        ManagedDocument document = documents.get(uri);
        return document == null ? Optional.empty() : Optional.ofNullable(document.parseResult);
    }

    public void updateDiagnostics(URI uri, List<LintDiagnostic> diagnostics) {
        ManagedDocument document = requireDocument(uri);
        synchronized (document) {
            document.diagnostics = List.copyOf(diagnostics);
        }
    }

    public List<LintDiagnostic> getLatestDiagnostics(URI uri) {
        ManagedDocument document = documents.get(uri);
        return document == null ? List.of() : document.diagnostics;
    }

    private ManagedDocument requireDocument(URI uri) {
        ManagedDocument document = documents.get(uri);
        if (document == null) {
            throw new IllegalArgumentException("No document is open for URI " + uri);
        }
        return document;
    }

    private EditApplicationResult applyEdit(String text, LineIndex lineIndex, TextDocumentContentChangeEvent change) {
        Range range = change.getRange();
        if (range == null) {
            String replacement = change.getText() == null ? "" : change.getText();
            return new EditApplicationResult(replacement, new TextSpan(0, replacement.length()));
        }

        TextSpan replacedSpan = lineIndex.spanOf(range);
        String replacement = change.getText() == null ? "" : change.getText();
        StringBuilder builder = new StringBuilder(text.length() - replacedSpan.length() + replacement.length());
        builder.append(text, 0, replacedSpan.startOffset());
        builder.append(replacement);
        builder.append(text, replacedSpan.endOffset(), text.length());

        TextSpan changedSpan = new TextSpan(
                replacedSpan.startOffset(),
                replacedSpan.startOffset() + replacement.length());
        return new EditApplicationResult(builder.toString(), changedSpan);
    }

    private static final class ManagedDocument {
        private volatile TextDocumentSnapshot snapshot;
        private volatile ParseResult parseResult;
        private volatile List<LintDiagnostic> diagnostics;

        private ManagedDocument(TextDocumentSnapshot snapshot) {
            this.snapshot = snapshot;
            this.diagnostics = Collections.emptyList();
        }
    }

    private record EditApplicationResult(String updatedText, TextSpan changedSpan) {
    }
}
