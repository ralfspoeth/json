package io.github.ralfspoeth.json.data;

import org.jspecify.annotations.Nullable;

/**
 * Representation of the JSON {@code null} type.
 */
public record JsonNull() implements Basic<Object> {
    public static final JsonNull INSTANCE = new JsonNull();

    /// @return "null"
    @Override
    public String json() {
        return "null";
    }

    ///  @return null;
    @Override
    public @Nullable Object value() {return null;}

    ///  true if {@code o instanceof JsonNull}
    @Override
    public boolean test(@Nullable JsonValue o) {
        return o instanceof JsonNull;
    }
}
