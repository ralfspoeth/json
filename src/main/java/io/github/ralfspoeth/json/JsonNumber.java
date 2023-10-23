package io.github.ralfspoeth.json;

public record JsonNumber(double value) implements Basic {
    public static final JsonNumber ZERO = new JsonNumber(0d);

    @Override
    public String json() {
        return Double.toString(value);
    }
}
