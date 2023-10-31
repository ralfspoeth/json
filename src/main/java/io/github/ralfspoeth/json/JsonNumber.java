package io.github.ralfspoeth.json;

public record JsonNumber(double numVal) implements Basic<Double> {
    public static final JsonNumber ZERO = new JsonNumber(0d);

    @Override
    public String json() {
        return Double.toString(numVal);
    }

    @Override
    public Double value() {
        return numVal;
    }
}
