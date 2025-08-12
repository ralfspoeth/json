package io.github.ralfspoeth.json;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
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

    default OptionalInt intValue() {
        return OptionalInt.empty();
    }

    default int intValue(int def) {
        return intValue().orElse(def);
    }

    default OptionalLong longValue() {
        return OptionalLong.empty();
    }

    default long longValue(long def) {
        return longValue().orElse(def);
    }

    default OptionalDouble doubleValue() {
        return OptionalDouble.empty();
    }

    default double doubleValue(double def) {
        return doubleValue().orElse(def);
    }
}
