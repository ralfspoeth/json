package io.github.ralfspoeth.json;

import java.math.BigDecimal;

public record JsonLong(long numVal) implements JsonNumber<Long> {
    @Override
    public String json() {
        return Long.toString(numVal);
    }

    @Override
    public boolean test(Long aLong) {
        return numVal == aLong;
    }

    @Override
    public Long value() {
        return numVal;
    }

    @Override
    public BigDecimal decimal() {
        return BigDecimal.valueOf(numVal);
    }
}
