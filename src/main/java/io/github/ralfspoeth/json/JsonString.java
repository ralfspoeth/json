package io.github.ralfspoeth.json;

import java.util.Objects;

public record JsonString(String value) implements Basic<String> {
    public JsonString {
        value = Objects.requireNonNullElse(value, "");
    }

    @Override
    public String json() {
        return "\"%s\"".formatted(value);
    }
}
