package io.github.ralfspoeth.json;

import java.util.Map;
import java.util.function.Predicate;

public sealed interface JsonValue extends Predicate<JsonValue> permits Aggregate, Basic {

    String json();

    int depth();

    static JsonValue of(Object o) {
        return switch(o) {
            case Record r -> JsonObject.ofRecord(r);
            case Map<?, ?> m -> JsonObject.ofMap(m);
            case Iterable<?> it -> JsonArray.ofIterable(it);
            case Object array when array.getClass().isArray() -> JsonArray.ofArray(array);
            case null, default -> Basic.of(o);
        };
    }

    @Override
    int hashCode();

    @Override
    boolean equals(Object o);
}
