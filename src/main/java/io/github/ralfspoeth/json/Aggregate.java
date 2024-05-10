package io.github.ralfspoeth.json;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.stream.StreamSupport;

import static io.github.ralfspoeth.basix.fn.Predicates.in;

public sealed interface Aggregate extends Element permits JsonArray, JsonObject {
    static JsonObjectBuilder objectBuilder() {
        return new JsonObjectBuilder();
    }

    static JsonObjectBuilder builder(Map<String, ? extends Element> map) {
        var bldr = objectBuilder();
        map.forEach(bldr::named);
        return bldr;
    }

    static JsonObjectBuilder builder(JsonObject from) {
        return builder(from.members());
    }

    static JsonArrayBuilder arrayBuilder() {
        return new JsonArrayBuilder();
    }

    static JsonArrayBuilder builder(Iterable<? extends Element> elems) {
        var ab = new JsonArrayBuilder();
        StreamSupport
                .stream(elems.spliterator(), false)
                .forEach(ab::item);
        return ab;
    }

    static JsonArrayBuilder builder(JsonArray from) {
        return builder(from.elements());
    }

    int size();

    int depth();

    sealed interface Builder<T extends Aggregate> {
        int size();

        default boolean isEmpty() {
            return size() == 0;
        }

        T build();
    }

    final class JsonObjectBuilder implements Builder<JsonObject> {

        private JsonObjectBuilder() {
        }

        private final Map<String, Element> data = new HashMap<>();

        public JsonObjectBuilder named(String name, Element el) {
            data.put(name, el);
            return this;
        }

        public JsonObjectBuilder basic(String name, Object o) {
            return named(name, Basic.of(o));
        }

        public JsonObjectBuilder basic(String name) {
            return named(name, JsonNull.INSTANCE);
        }

        public JsonObjectBuilder update(Map<String, ? extends Element> map) {
            map.entrySet().stream()
                    .filter(in(data.keySet(), Map.Entry::getKey))
                    .forEach(e -> named(e.getKey(), e.getValue()));
            return this;
        }

        public JsonObjectBuilder update(JsonObject o) {
            return update(o.members());
        }

        public JsonObjectBuilder merge(Map<String, ? extends Element> map) {
            map.forEach(this::named);
            return this;
        }

        public JsonObjectBuilder merge(JsonObject o) {
            return merge(o.members());
        }

        public JsonObjectBuilder remove(String key) {
            data.remove(key);
            return this;
        }

        public JsonObjectBuilder removeAll(Collection<String> keys) {
            keys.forEach(data::remove);
            return this;
        }

        public JsonObjectBuilder removeAll(JsonObject o) {
            return removeAll(o.members().keySet());
        }

        @Override
        public JsonObject build() {
            return new JsonObject(data);
        }

        @Override
        public int size() {
            return data.size();
        }
    }

    final class JsonArrayBuilder implements Builder<JsonArray> {
        private JsonArrayBuilder() {
        }

        public int size() {
            return data.size();
        }

        @Override
        public JsonArray build() {
            return new JsonArray(
                    data.stream().map(item -> switch (item) {
                        case Element je -> je;
                        case Builder<?> jb -> jb.build();
                        default -> throw new AssertionError();
                    }).toList()
            );
        }

        private final List<Object> data = new ArrayList<>();

        public JsonArrayBuilder item(Element elem) {
            data.add(elem);
            return this;
        }

        public JsonArrayBuilder basic(Object o) {
            return item(Basic.of(o));
        }

        public JsonArrayBuilder aggregate(Object o) {
            return item(Aggregate.of(o));
        }

        public JsonArrayBuilder element(Object o) {
            return item(Element.of(o));
        }

        public JsonArrayBuilder nullItem() {
            return item(JsonNull.INSTANCE);
        }
    }

    private static JsonArray ofIterable(Iterable<?> iterable) {
        var ab = new JsonArrayBuilder();
        for (var it : iterable) {
            ab.item(Element.of(it));
        }
        return ab.build();
    }

    private static <R extends Record> JsonObject ofRecord(R r) {
        var rc = r.getClass().getRecordComponents();
        var ob = new JsonObjectBuilder();
        for (RecordComponent comp : rc) {
            var name = comp.getName();
            try {
                var value = comp.getAccessor().invoke(r);
                ob.named(name, Element.of(value));
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        return ob.build();
    }

    private static JsonArray ofArray(Object o) {
        var ab = arrayBuilder();
        for (int i = 0, len = Array.getLength(o); i < len; i++) {
            ab.item(Element.of(Array.get(o, i)));
        }
        return ab.build();
    }

    static Aggregate of(Object o) {
        return switch (o) {
            case Record r -> ofRecord(r);
            case Object a when a.getClass().isArray() -> ofArray(a);
            case Iterable<?> it -> ofIterable(it);
            case null, default -> throw new IllegalArgumentException("Cannot turn into an aggregate: " + o);
        };
    }
}
