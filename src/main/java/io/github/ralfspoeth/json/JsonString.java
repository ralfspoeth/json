package io.github.ralfspoeth.json;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public record JsonString(String value) implements Basic<String> {
    public JsonString {
        value = Objects.requireNonNullElse(value, "");
    }

    @Override
    public <E extends Enum<E>> E enumValue(Class<E> enumClass) {
        return Enum.valueOf(enumClass, value);
    }

    @Override
    public <E extends Enum<E>> E enumValueIgnoreCase(Class<E> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants())
                .collect(Collectors.toMap(c -> c.name().toUpperCase(), c -> c))
                .get(value.toUpperCase());
    }

    @Override
    public boolean booleanValue() {
        if(value.equals("true")) {
            return true;
        }
        else if(value.equals("false")) {
            return false;
        }
        else {
            throw new IllegalStateException("%s is neither 'true' nor 'false'".formatted(value));
        }
    }

    @Override
    public String json() {
        return "\"%s\"".formatted(value);
    }
}
