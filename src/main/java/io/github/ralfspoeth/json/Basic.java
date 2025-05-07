package io.github.ralfspoeth.json;

import java.util.function.Predicate;

public sealed interface Basic<T> extends Element, Predicate<T> permits JsonBoolean, JsonNull, JsonNumber, JsonString {

    String json();
    T value();

    @Override
    default int depth() {
        return 1;
    }

    static Basic<?> of(Object o) {
        return switch(o) {
            case null -> JsonNull.INSTANCE;
            case Boolean b -> JsonBoolean.of(b);
            case Double d -> ofDouble(d);
            case Float f -> ofDouble(f);
            case Number n -> ofDouble(n.doubleValue());
            default -> ofString(o.toString());
        };
    }
    private static JsonNumber ofDouble(double d) {
        return new JsonNumber(d);
    }

    private static JsonString ofString(String s) {
        return new JsonString(s);
    }
}
