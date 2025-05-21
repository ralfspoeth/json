package io.github.ralfspoeth.json;

import java.math.BigDecimal;

public record JsonDouble(double numVal) implements JsonNumber<Double> {
    public static final JsonDouble ZERO = new JsonDouble(0d);

    @Override
    public String json() {
        return Double.toString(numVal);
    }

    @Override
    public Double value() {
        return numVal;
    }

    @Override
    public boolean test(Double aDouble) {
        return Double.compare(aDouble, numVal) == 0;
    }

    @Override
    public BigDecimal decimal() {
        return BigDecimal.valueOf(numVal);
    }
}
