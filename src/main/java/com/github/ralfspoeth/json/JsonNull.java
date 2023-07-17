package com.github.ralfspoeth.json;

public enum JsonNull implements JsonValue {
    INSTANCE;

    @Override
    public String json() {
        return "null";
    }
}
