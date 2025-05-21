package io.github.ralfspoeth.json;

import java.math.BigDecimal;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public record JsonBigDecimal(BigDecimal value) implements JsonNumber<BigDecimal> {

    public JsonBigDecimal {
        requireNonNull(value);
    }

    @Override
    public String json() {
        return value.toString();
    }

    @Override
    public boolean test(BigDecimal bigDecimal) {
        return Objects.equals(value, bigDecimal);
    }

    @Override
    public BigDecimal decimal() {
        return value();
    }
}
