package com.github.ralfspoeth.json.data;

public record JsonTrue() implements JsonValue {
    public static final JsonTrue INSTANCE = new JsonTrue();
}
