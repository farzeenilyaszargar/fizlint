package com.livelinter.documents;

import java.net.URI;

public final class TextDocumentSnapshot {
    private final URI uri;
    private final String text;
    private final int version;
    private final LineIndex lineIndex;

    public TextDocumentSnapshot(URI uri, String text, int version) {
        this.uri = uri;
        this.text = text;
        this.version = version;
        this.lineIndex = new LineIndex(text);
    }

    public URI uri() {
        return uri;
    }

    public String text() {
        return text;
    }

    public int version() {
        return version;
    }

    public LineIndex lineIndex() {
        return lineIndex;
    }
}
