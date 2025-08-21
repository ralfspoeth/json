package io.github.ralfspoeth.json;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

public value record JsonNumber(BigDecimal value) implements Basic<BigDecimal> {
    public static final JsonNumber ZERO = new JsonNumber(BigDecimal.ZERO);

    public JsonNumber {
        value = Objects.requireNonNull(value).stripTrailingZeros();
    }

    @Override
    public String json() {
        return value.toString();
    }

    @Override
    public boolean test(JsonValue other) {
        return other instanceof JsonNumber(BigDecimal bd) && bd.equals(value);
    }

    @Override
    public OptionalDouble doubleValue() {
        return OptionalDouble.of(value.doubleValue());
    }

    @Override
    public OptionalInt intValue() {
        return OptionalInt.of(value.intValue());
    }

    @Override
    public OptionalLong longValue() {
        return OptionalLong.of(value.longValue());
    }
}
