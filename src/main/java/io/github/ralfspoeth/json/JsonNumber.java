package io.github.ralfspoeth.json;

import java.math.BigDecimal;
import java.util.Objects;

public record JsonNumber(BigDecimal numVal) implements Basic<BigDecimal> {
    public static final JsonNumber ZERO = new JsonNumber(BigDecimal.ZERO);

    public JsonNumber {
        Objects.requireNonNull(numVal);
    }

    public JsonNumber(double d) {
        this(BigDecimal.valueOf(d));
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
        return other instanceof JsonNumber(BigDecimal bd) && bd.compareTo(value())==0;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof JsonNumber(BigDecimal other)) {
            return numVal.compareTo(other) == 0;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Double.hashCode(numVal.doubleValue());
    }
}
