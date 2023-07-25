package com.github.ralfspoeth.json;

public sealed interface Basic extends Element permits JsonBoolean, JsonNull, JsonNumber, JsonString {

    String json();

    static Basic of(Object o) {
        return switch(o) {
            case null -> JsonNull.INSTANCE;
            case Boolean b -> JsonBoolean.of(b);
            case Double d -> ofDouble(d);
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
