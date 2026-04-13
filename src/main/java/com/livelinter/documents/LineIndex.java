package com.livelinter.documents;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public final class LineIndex {
    private final String text;
    private final int[] lineStarts;

    public LineIndex(String text) {
        this.text = text;
        this.lineStarts = computeLineStarts(text);
    }

    public int offsetAt(Position position) {
        int line = Math.max(0, Math.min(position.getLine(), lineStarts.length - 1));
        int lineStart = lineStarts[line];
        int lineEnd = line + 1 < lineStarts.length ? lineStarts[line + 1] : text.length();
        int character = Math.max(0, position.getCharacter());
        return Math.min(lineStart + character, lineEnd);
    }

    public Position positionAt(int offset) {
        int boundedOffset = Math.max(0, Math.min(offset, text.length()));
        int line = findLine(boundedOffset);
        return new Position(line, boundedOffset - lineStarts[line]);
    }

    public Range rangeOf(TextSpan span) {
        return new Range(positionAt(span.startOffset()), positionAt(span.endOffset()));
    }

    public TextSpan spanOf(Range range) {
        return new TextSpan(offsetAt(range.getStart()), offsetAt(range.getEnd()));
    }

    public String lineText(int line) {
        if (line < 0 || line >= lineStarts.length) {
            return "";
        }
        int start = lineStarts[line];
        int end = line + 1 < lineStarts.length ? lineStarts[line + 1] : text.length();
        while (end > start && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) {
            end--;
        }
        return text.substring(start, end);
    }

    public int lineCount() {
        return lineStarts.length;
    }

    private static int[] computeLineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n' && i + 1 <= text.length()) {
                starts.add(i + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    private int findLine(int offset) {
        int low = 0;
        int high = lineStarts.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int lineStart = lineStarts[mid];
            int nextStart = mid + 1 < lineStarts.length ? lineStarts[mid + 1] : Integer.MAX_VALUE;
            if (offset < lineStart) {
                high = mid - 1;
            } else if (offset >= nextStart) {
                low = mid + 1;
            } else {
                return mid;
            }
        }
        return Math.max(0, Math.min(low, lineStarts.length - 1));
    }
}
