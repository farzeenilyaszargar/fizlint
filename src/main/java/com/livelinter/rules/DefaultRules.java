package com.livelinter.rules;

import java.util.List;

public final class DefaultRules {
    private DefaultRules() {
    }

    public static List<Rule> create() {
        return List.of(
                new TrailingWhitespaceRule(),
                new LineLengthRule(120),
                new UnusedVariableRule(),
                new UnreachableCodeRule(),
                new LogicalPatternRule());
    }
}
