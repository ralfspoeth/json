package io.github.ralfspoeth.json.data;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * JSON {@code true} and {@code false} equivalents.
 *
 * @param boolValue
 */
public record JsonBoolean(boolean boolValue) implements Basic<Boolean> {
    /// The {@code true} representation
    public static final JsonBoolean TRUE = new JsonBoolean(true);
    ///  The {@code false} representation
    public static final JsonBoolean FALSE = new JsonBoolean(false);
    /// {@snippet : b?TRUE:FALSE}
    public static JsonBoolean of(boolean b) {
        return b ? TRUE : FALSE;
    }

    @Override
    public Optional<Boolean> bool() {
        return Optional.of(boolValue);
    }

    /**
     * The value is the boxed Boolean.
     */
    @Override
    public Boolean value() {
        return boolValue;
    }

    /**
     * {@code true} if and only if {@code jv} is a {@link JsonBoolean}
     * and the {@snippet : this.value().equals(jv.value());}
     */
    @Override
    public boolean test(@Nullable JsonValue jv) {
        return jv instanceof JsonBoolean(boolean value) && value == boolValue;
    }

    /**
     * {@code TRUE -> "true"} and {@code FALSE -> "false"}
     */
    @Override
    public String json() {
        return Boolean.toString(boolValue);
    }
}

