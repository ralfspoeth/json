package io.github.ralfspoeth.json;

public sealed interface Element permits Aggregate, Basic {

    static Element of(Object o) {
        if (o instanceof Record || o instanceof Iterable<?> || o != null && o.getClass().isArray()) {
            return Aggregate.of(o);
        } else {
            return Basic.of(o);
        }
    }
}
