package com.pd.json.data;

public record JsonString(CharSequence value) implements JsonValue {
    public static JsonString of(CharSequence value) {
        return new JsonString(value);
    }
}
