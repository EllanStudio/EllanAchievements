package dev.ellan.achievements.paper.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;

/** Append-only record of admin changes to player progress. */
public final class AuditLog {
    private final Path file;

    public AuditLog(Path file) {
        this.file = file;
    }

    public synchronized void append(String operator, String player, String achievement, String before, String after)
        throws IOException {
        String line = String.join("\t", OffsetDateTime.now().toString(), operator, player, achievement, before, after);
        Files.createDirectories(file.getParent());
        Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
