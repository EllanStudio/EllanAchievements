package dev.ellan.achievements.core.config;

import dev.ellan.achievements.core.event.Triggers;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One {@code filter} entry: a set of accepted values, or a numeric comparison. */
public sealed interface FilterValue {
    /** Whether an event attribute value passes. A missing attribute ({@code null}) never passes. */
    boolean test(Object attribute);

    /** Parses a YAML value; throws {@link IllegalArgumentException} with a readable reason when invalid. */
    static FilterValue parse(String trigger, String attribute, Object yaml) {
        if (Triggers.isNumeric(trigger, attribute)) {
            return Compare.parse(yaml);
        }
        if (yaml instanceof List<?> list) {
            if (list.isEmpty()) {
                throw new IllegalArgumentException("empty list");
            }
            Set<String> values = new LinkedHashSet<>();
            for (Object element : list) {
                values.add(AnyOf.scalar(element));
            }
            return new AnyOf(Set.copyOf(values));
        }
        return new AnyOf(Set.of(AnyOf.scalar(yaml)));
    }

    /** Accepts any of the given values, case-insensitively. A list attribute passes if any element does. */
    record AnyOf(Set<String> values) implements FilterValue {
        private static String scalar(Object value) {
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                return value.toString().toLowerCase(Locale.ROOT);
            }
            throw new IllegalArgumentException("expected a value or a list of values, got " + value);
        }

        @Override
        public boolean test(Object attribute) {
            if (attribute == null) {
                return false;
            }
            if (attribute instanceof Collection<?> collection) {
                return collection.stream().anyMatch(this::test);
            }
            if (attribute instanceof Map<?, ?>) {
                return false;
            }
            return values.contains(attribute.toString().toLowerCase(Locale.ROOT));
        }
    }

    /** A numeric comparison such as {@code >=50}. */
    record Compare(String op, double value) implements FilterValue {
        private static final Pattern PATTERN = Pattern.compile("^(>=|<=|==|>|<)\\s*(-?\\d+(?:\\.\\d+)?)$");

        private static Compare parse(Object yaml) {
            if (yaml instanceof Number number) {
                return new Compare("==", number.doubleValue());
            }
            if (yaml instanceof String text) {
                Matcher matcher = PATTERN.matcher(text.trim());
                if (matcher.matches()) {
                    return new Compare(matcher.group(1), Double.parseDouble(matcher.group(2)));
                }
            }
            throw new IllegalArgumentException("expected a number or a comparison like \">=50\", got " + yaml);
        }

        @Override
        public boolean test(Object attribute) {
            if (!(attribute instanceof Number number)) {
                return false;
            }
            double actual = number.doubleValue();
            return switch (op) {
                case ">=" -> actual >= value;
                case "<=" -> actual <= value;
                case ">" -> actual > value;
                case "<" -> actual < value;
                default -> actual == value;
            };
        }
    }
}
