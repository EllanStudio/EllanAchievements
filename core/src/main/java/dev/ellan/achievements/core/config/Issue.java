package dev.ellan.achievements.core.config;

/** A config problem. {@code achievement} is null for tab- or file-level problems. */
public record Issue(Severity severity, String file, String achievement, String field, String message) {
    @Override
    public String toString() {
        return severity + " " + file + (achievement == null ? "" : " / " + achievement) + " / " + field + ": " + message;
    }
}
