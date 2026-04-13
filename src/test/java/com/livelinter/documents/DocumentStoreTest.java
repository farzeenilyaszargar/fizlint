package com.livelinter.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.junit.jupiter.api.Test;

class DocumentStoreTest {
    @Test
    void appliesIncrementalChangesAndRejectsStaleVersions() {
        DocumentStore store = new DocumentStore();
        URI uri = URI.create("file:///Example.java");
        store.didOpen(uri, "class A {}\n", 1);

        TextDocumentContentChangeEvent insert = new TextDocumentContentChangeEvent(
                new Range(new Position(0, 8), new Position(0, 8)),
                0,
                "main ");
        DocumentUpdateResult update = store.didChange(uri, List.of(insert), 2);

        assertFalse(update.stale());
        assertEquals("class A main {}\n", update.snapshot().text());
        assertEquals(new TextSpan(8, 13), update.changedSpan());

        TextDocumentContentChangeEvent staleEdit = new TextDocumentContentChangeEvent(
                new Range(new Position(0, 0), new Position(0, 5)),
                5,
                "interface");
        DocumentUpdateResult stale = store.didChange(uri, List.of(staleEdit), 1);

        assertTrue(stale.stale());
        assertEquals(2, stale.snapshot().version());
        assertEquals("class A main {}\n", stale.snapshot().text());
    }
}
