package io.github.ralfspoeth.json.data;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;

public record JsonNumber(BigDecimal value) implements Basic<BigDecimal> {
    public static final JsonNumber ZERO = new JsonNumber(BigDecimal.ZERO);

    public JsonNumber {
        value = Objects.requireNonNull(value).stripTrailingZeros();
    }

    @Override
    public String json() {
        return value.toString();
    }

    @Override
    public boolean test(@Nullable JsonValue other) {
        return other instanceof JsonNumber(BigDecimal bd) && bd.equals(value);
    }

    @Override
    public Optional<Double> doubleValue() {
        return Optional.of(value.doubleValue());
    }

    @Override
    public Optional<Integer> intValue() {
        return Optional.of(value.intValue());
    }

    @Override
    public Optional<Long> longValue() {
        return Optional.of(value.longValue());
    }

    @Override
    public Optional<BigDecimal> decimalValue() {
        return Optional.of(value);
    }
}
