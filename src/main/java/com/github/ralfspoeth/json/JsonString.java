package com.github.ralfspoeth.json;

import java.util.Objects;

public record JsonString(CharSequence value) implements Basic {
    public JsonString {
        value = Objects.requireNonNullElse(value, "");
    }

    @Override
    public String json() {
        return "\"%s\"".formatted(value);
    }
}
