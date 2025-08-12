package io.github.ralfspoeth.json;

import java.math.BigDecimal;

public sealed interface Basic<T> extends JsonValue permits JsonBoolean, JsonNull, JsonNumber, JsonString {

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
            case Byte b -> new JsonNumber(BigDecimal.valueOf(b));
            case Character c -> new JsonNumber(BigDecimal.valueOf(c));
            case Short s -> new JsonNumber(BigDecimal.valueOf(s));
            case Integer i -> new JsonNumber(BigDecimal.valueOf(i));
            case Long l -> new JsonNumber(BigDecimal.valueOf(l));
            case Float f -> new JsonNumber(BigDecimal.valueOf(f));
            case Double d -> new JsonNumber(BigDecimal.valueOf(d));
            case Number n -> new JsonNumber(BigDecimal.valueOf(n.doubleValue()));
            case Boolean b -> JsonBoolean.of(b);
            default -> new JsonString(o.toString());
        };
    }
}
