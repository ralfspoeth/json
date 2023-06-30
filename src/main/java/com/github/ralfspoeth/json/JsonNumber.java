package com.github.ralfspoeth.json;

public record JsonNumber(double value) implements JsonValue {
    public static final JsonNumber ZERO = new JsonNumber(0d);
    public JsonNumber(java.lang.String value) {
        this(Double.parseDouble(value));
    }
}
