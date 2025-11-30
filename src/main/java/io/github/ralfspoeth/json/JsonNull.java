package io.github.ralfspoeth.json;

import org.jspecify.annotations.Nullable;

public record JsonNull() implements Basic<Object> {
    public static final JsonNull INSTANCE = new JsonNull();

    @Override
    public String json() {
        return "null";
    }

    @Override
    public @Nullable Object value() {return null;}

    @Override
    public boolean test(JsonValue o) {
        return o instanceof JsonNull;
    }
}
