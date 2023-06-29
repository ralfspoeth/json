package com.github.ralfspoeth.json.data;

public record JsonNull() implements JsonValue {
    public static final JsonNull INSTANCE = new JsonNull();
}
