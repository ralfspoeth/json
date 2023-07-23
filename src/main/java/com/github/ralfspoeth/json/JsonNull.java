package com.github.ralfspoeth.json;

public final class JsonNull implements JsonValue {
    public static final JsonNull INSTANCE = new JsonNull();

    private JsonNull(){}

    @Override
    public String json() {
        return "null";
    }
}
