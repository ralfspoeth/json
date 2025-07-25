package io.github.ralfspoeth.json;

import java.math.BigDecimal;
import java.util.function.Predicate;

public sealed interface Basic<T> extends JsonValue, Predicate<JsonValue> permits JsonBoolean, JsonNull, JsonNumber, JsonString {

    String json();
    T value();

    @Override
    default int depth() {
        return 1;
    }

    static Basic<?> of(Object o) {
        return switch(o) {
            case null -> JsonNull.INSTANCE;
            case BigDecimal bd -> new JsonNumber(bd);
            case Integer i -> new JsonNumber(BigDecimal.valueOf(i));
            case Long l -> new JsonNumber(BigDecimal.valueOf(l));
            case Double d -> new JsonNumber(d);
            case Number n -> new JsonNumber(n.doubleValue());
            case Boolean b -> JsonBoolean.of(b);
            default -> new JsonString(o.toString());
        };
    }
}
