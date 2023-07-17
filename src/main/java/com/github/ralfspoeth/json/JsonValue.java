package com.github.ralfspoeth.json;

public sealed interface JsonValue extends JsonElement permits JsonBoolean, JsonNull, JsonNumber, JsonString {

    String json();

    static JsonValue of(Object o) {
        return switch(o) {
            case null -> ofNull();
            case Boolean b -> JsonBoolean.of(b);
            case Double d -> ofDouble(d);
            default -> ofString(o.toString());
        };
    }
    private static JsonNumber ofDouble(double d) {
        return new JsonNumber(d);
    }

    private static JsonNull ofNull() {
        return JsonNull.INSTANCE;
    }

    private static JsonString ofString(String s) {
        return new JsonString(s);
    }
}
