package io.github.ralfspoeth.json;

public sealed interface Element permits Aggregate, Basic {

    static Element of(Object o) {
        return switch(o) {
            case Record r -> JsonObject.ofRecord(r);
            case Iterable<?> it -> JsonArray.ofIterable(it);
            case Object array when array.getClass().isArray() -> JsonArray.ofArray(array);
            case null, default -> Basic.of(o);
        };
    }

    static Basic of(int i) {
        return new JsonNumber(i);
    }

    static Basic of(long l) {
        return new JsonNumber(l);
    }

    static Basic of(double d) {
        return new JsonNumber(d);
    }

    static Basic of(boolean b) {
        return JsonBoolean.of(b);
    }
}
