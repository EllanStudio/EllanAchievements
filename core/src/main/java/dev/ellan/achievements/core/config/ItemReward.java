package dev.ellan.achievements.core.config;

/** One reward item, written in config as {@code "namespace:id*amount"} (amount defaults to 1). */
public record ItemReward(String itemId, int amount) {
    public static ItemReward parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("empty reward item");
        }
        String value = text.trim();
        int star = value.lastIndexOf('*');
        if (star < 0) {
            return new ItemReward(value, 1);
        }
        String id = value.substring(0, star).trim();
        int amount;
        try {
            amount = Integer.parseInt(value.substring(star + 1).trim());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid amount in " + text);
        }
        if (id.isEmpty() || amount < 1) {
            throw new IllegalArgumentException("invalid reward item " + text);
        }
        return new ItemReward(id, amount);
    }
}
