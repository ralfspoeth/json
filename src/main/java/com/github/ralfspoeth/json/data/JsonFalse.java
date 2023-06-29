package com.github.ralfspoeth.json.data;

public record JsonFalse() implements JsonValue {
    public static final JsonFalse INSTANCE = new JsonFalse();
}
