package dev.ellan.achievements.core.rules;

public record ProgressChange(String fullId, long before, long after, long goal) {
}
