package com.livelinter.documents;

public record TextSpan(int startOffset, int endOffset) {
    public TextSpan {
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("Invalid text span: " + startOffset + " to " + endOffset);
        }
    }

    public static TextSpan emptyAt(int offset) {
        return new TextSpan(offset, offset);
    }

    public int length() {
        return endOffset - startOffset;
    }

    public boolean contains(int offset) {
        return offset >= startOffset && offset <= endOffset;
    }

    public boolean overlaps(TextSpan other) {
        return startOffset <= other.endOffset && other.startOffset <= endOffset;
    }

    public TextSpan union(TextSpan other) {
        return new TextSpan(Math.min(startOffset, other.startOffset), Math.max(endOffset, other.endOffset));
    }
}
