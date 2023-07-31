package com.github.ralfspoeth.json;

public sealed interface Basic extends Element permits JsonBoolean, JsonNull, JsonNumber, JsonString {

    String json();

    static Basic of(Object o) {
        return switch(o) {
            case null -> JsonNull.INSTANCE;
            case Boolean b -> JsonBoolean.of(b);
            case Number n -> ofDouble(n.doubleValue());
            default -> ofString(o.toString());
        };
    }
    private static JsonNumber ofDouble(double d) {
        return new JsonNumber(d);
    }

    private static JsonString ofString(String s) {
        return new JsonString(s);
    }
}
