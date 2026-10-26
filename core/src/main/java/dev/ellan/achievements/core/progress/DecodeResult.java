package dev.ellan.achievements.core.progress;

public sealed interface DecodeResult {
    record Ok(PlayerProgress progress) implements DecodeResult {
    }

    /** Stored data that this version cannot read; it must be kept as-is, never overwritten. */
    record Unreadable(String raw, String reason) implements DecodeResult {
    }
}
