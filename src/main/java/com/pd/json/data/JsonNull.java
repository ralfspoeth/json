package com.pd.json.data;

public record JsonNull() implements JsonValue {
    public static final JsonNull INSTANCE = new JsonNull();
}
