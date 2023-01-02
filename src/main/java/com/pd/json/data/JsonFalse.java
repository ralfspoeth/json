package com.pd.json.data;

public record JsonFalse() implements JsonValue {
    public static final JsonFalse INSTANCE = new JsonFalse();
}
