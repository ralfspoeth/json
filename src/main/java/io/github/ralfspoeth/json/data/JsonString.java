package io.github.ralfspoeth.json.data;

import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Objects.requireNonNull;

/**
 * Represents a JSON string.
 *
 * @param value the string contents, must not be {@code null}.
 */
public record JsonString(String value) implements Basic<String> {
    public JsonString {
        value = requireNonNull(value);
    }

    /**
     * The escaped JSON string representation.
     */
    @Override
    public String json() {
        return "\"%s\"".formatted(escaped(value));
    }

    private static final ConcurrentMap<String, String> cachedEscaped = new ConcurrentHashMap<>();

    /**
     * Obtain the escaped version of the given string {@code s}.
     * @param s a string, may not be {@code null}
     * @see <a href="http://json.org">json.org</a>
     */
    public static String escaped(String s) {
        return cachedEscaped.computeIfAbsent(s, JsonString::escape);
    }

    private static String escape(String s) {
        var sb = new StringBuilder(s.length() * 2);
        s.chars().forEach(c -> {
            switch(c) {
                case '\\' -> sb.append("\\\\");
                case '\"' -> sb.append("\\\"");
                case '/' -> sb.append("\\/");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if(0x00 <= c && c <= 0x1F) {
                        sb.append("\\u%04x".formatted(c));
                    } else {
                        sb.append((char)c);
                    }
                }
            }
        });
        return sb.toString();
    }

    ///  @return a non-empty optional with {@code this.value()} as payload
    @Override
    public Optional<String> string() {
        return Optional.of(value);
    }

    /// {@code true} if {@code s} is another {@link JsonString}
    /// and the contents of {@code this} and {@code s} are equal.
    @Override
    public boolean test(@Nullable JsonValue s) {
        return s instanceof JsonString(String v) && v.equals(value);
    }
}
