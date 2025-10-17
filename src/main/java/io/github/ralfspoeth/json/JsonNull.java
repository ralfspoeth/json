package io.github.ralfspoeth.json;

import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

public value record JsonNull() implements Basic<Object> {
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

    @Override
    public Optional<Boolean> booleanValue() {
        return Optional.of(false);
    }

    @Override
    public OptionalInt intValue() {
        return OptionalInt.of(0);
    }

    @Override
    public OptionalLong longValue() {
        return OptionalLong.of(0L);
    }

    @Override
    public OptionalDouble doubleValue() {
        return OptionalDouble.of(0d);
    }
}
