package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.*;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static java.util.Objects.requireNonNull;

public class OptionalValue {
    private final JsonValue value;

    private OptionalValue(JsonValue value) {
        this.value = value;
    }

    public static OptionalValue ofNullable(JsonValue value) {
        return new OptionalValue(value);
    }

    public static OptionalValue of(JsonValue value) {
        return new OptionalValue(requireNonNull(value));
    }

    public OptionalInt intValue() {
        return switch(value) {
            case Basic<?> basic -> basic.intValue();
            case null, default -> OptionalInt.empty();
        };
    }

    public OptionalLong longValue() {
        return switch (value) {
            case Basic<?> basic -> basic.longValue();
            case null, default -> OptionalLong.empty();
        };
    }

    public OptionalDouble doubleValue(){
        return switch (value) {
            case Basic<?> basic -> basic.doubleValue();
            case null, default -> OptionalDouble.empty();
        };
    }

    public int intValue(int def) {
        return intValue().orElse(def);
    }

    public long longValue(long def) {
        return longValue().orElse(def);
    }

}
