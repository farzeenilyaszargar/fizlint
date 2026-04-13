package com.livelinter.diagnostics;

import com.livelinter.documents.TextSpan;
import java.util.Objects;
import java.util.Optional;

public final class LintDiagnostic {
    private final String code;
    private final String source;
    private final String message;
    private final LintSeverity severity;
    private final TextSpan span;
    private final String suggestion;
    private final String fixTitle;
    private final String replacementText;

    private LintDiagnostic(Builder builder) {
        this.code = Objects.requireNonNull(builder.code, "code");
        this.source = Objects.requireNonNullElse(builder.source, "live-linter");
        this.message = Objects.requireNonNull(builder.message, "message");
        this.severity = Objects.requireNonNull(builder.severity, "severity");
        this.span = Objects.requireNonNull(builder.span, "span");
        this.suggestion = builder.suggestion;
        this.fixTitle = builder.fixTitle;
        this.replacementText = builder.replacementText;
    }

    public String code() {
        return code;
    }

    public String source() {
        return source;
    }

    public String message() {
        return message;
    }

    public LintSeverity severity() {
        return severity;
    }

    public TextSpan span() {
        return span;
    }

    public Optional<String> suggestion() {
        return Optional.ofNullable(suggestion);
    }

    public Optional<String> fixTitle() {
        return Optional.ofNullable(fixTitle);
    }

    public Optional<String> replacementText() {
        return Optional.ofNullable(replacementText);
    }

    public String hoverMessage() {
        return suggestion == null ? message : message + "\n\nSuggestion: " + suggestion;
    }

    public static Builder builder(String code, String message, LintSeverity severity, TextSpan span) {
        return new Builder(code, message, severity, span);
    }

    public static final class Builder {
        private final String code;
        private final String message;
        private final LintSeverity severity;
        private final TextSpan span;
        private String source;
        private String suggestion;
        private String fixTitle;
        private String replacementText;

        private Builder(String code, String message, LintSeverity severity, TextSpan span) {
            this.code = code;
            this.message = message;
            this.severity = severity;
            this.span = span;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder suggestion(String suggestion) {
            this.suggestion = suggestion;
            return this;
        }

        public Builder quickFix(String title, String replacementText) {
            this.fixTitle = title;
            this.replacementText = replacementText;
            return this;
        }

        public LintDiagnostic build() {
            return new LintDiagnostic(this);
        }
    }
}
