package com.livelinter.documents;

public record DocumentUpdateResult(TextDocumentSnapshot snapshot, TextSpan changedSpan, boolean stale) {
}
