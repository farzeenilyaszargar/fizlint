package com.livelinter.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

class LineIndexTest {
    @Test
    void convertsBetweenOffsetsAndPositions() {
        LineIndex lineIndex = new LineIndex("first\nsecond\nthird");

        assertEquals(0, lineIndex.offsetAt(new Position(0, 0)));
        assertEquals(8, lineIndex.offsetAt(new Position(1, 2)));
        assertEquals(new Position(2, 1), lineIndex.positionAt(14));

        TextSpan span = lineIndex.spanOf(new Range(new Position(1, 0), new Position(1, 6)));
        assertEquals(new TextSpan(6, 12), span);
        assertEquals(new Range(new Position(1, 0), new Position(1, 6)), lineIndex.rangeOf(span));
    }
}
