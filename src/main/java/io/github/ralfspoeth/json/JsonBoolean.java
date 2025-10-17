package io.github.ralfspoeth.json;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

public value record JsonBoolean(boolean boolValue) implements Basic<Boolean> {
    public static final JsonBoolean TRUE = new JsonBoolean(true);
    public static final JsonBoolean FALSE = new JsonBoolean(false);

    public static JsonBoolean of(boolean b) {
        return b ? TRUE : FALSE;
    }

    @Override
    public Optional<Boolean> booleanValue() {
        return Optional.of(boolValue);
    }

    @Override
    public Boolean value() {
        return boolValue;
    }

    @Override
    public boolean test(JsonValue aBoolean) {
        return aBoolean instanceof JsonBoolean(boolean value) && value == boolValue;
    }

    @Override
    public String json() {
        return Boolean.toString(boolValue);
    }

    @Override
    public OptionalInt intValue() {
        return OptionalInt.of(boolValue ? 1 : 0);
    }

    @Override
    public OptionalLong longValue() {
        return OptionalLong.of(boolValue ? 1L : 0L);
    }

    @Override
    public OptionalDouble doubleValue() {
        return OptionalDouble.of(boolValue ? 1d : 0d);
    }
}
