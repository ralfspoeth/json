package com.github.ralfspoeth.json;

import java.util.Objects;

public record JsonString(CharSequence value) implements JsonValue {
    public JsonString {
        value = Objects.requireNonNullElse(value, "");
    }
}