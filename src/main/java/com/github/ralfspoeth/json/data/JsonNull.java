package com.github.ralfspoeth.json.data;

public final class JsonNull implements JsonValue {
    private JsonNull(){}
    public static final JsonNull INSTANCE = new JsonNull();
}
