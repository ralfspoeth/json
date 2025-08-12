package io.github.ralfspoeth.json;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

public record JsonNumber(BigDecimal numVal) implements Basic<BigDecimal> {
    public static final JsonNumber ZERO = new JsonNumber(BigDecimal.ZERO);

    public JsonNumber {
        numVal = Objects.requireNonNull(numVal).stripTrailingZeros();
    }

    @Override
    public String json() {
        return numVal.toString();
    }

    @Override
    public BigDecimal value() {
        return numVal();
    }

    @Override
    public boolean test(JsonValue other) {
        return other instanceof JsonNumber(BigDecimal bd) && bd.equals(numVal);
    }

    @Override
    public OptionalDouble doubleValue() {
        return OptionalDouble.of(numVal.doubleValue());
    }

    @Override
    public OptionalInt intValue() {
        return OptionalInt.of(numVal.intValue());
    }

    @Override
    public OptionalLong longValue() {
        return OptionalLong.of(numVal.longValue());
    }
}
