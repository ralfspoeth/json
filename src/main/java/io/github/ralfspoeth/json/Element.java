package io.github.ralfspoeth.json;

import java.util.Optional;
import java.util.stream.Stream;

public sealed interface Element permits Aggregate, Basic {

    static Element of(Object o) {
        return switch(o) {
            case Record r -> JsonObject.ofRecord(r);
            case Iterable<?> it -> JsonArray.ofIterable(it);
            case Object array when array.getClass().isArray() -> JsonArray.ofArray(array);
            case null, default -> Basic.of(o);
        };
    }
}
