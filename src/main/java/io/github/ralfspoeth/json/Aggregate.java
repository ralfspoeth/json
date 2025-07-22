package io.github.ralfspoeth.json;

import java.util.*;

import static io.github.ralfspoeth.basix.fn.Predicates.in;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toUnmodifiableMap;

public sealed interface Aggregate extends JsonValue permits JsonArray, JsonObject {
    static JsonObjectBuilder objectBuilder() {
        return new JsonObjectBuilder();
    }

    static JsonObjectBuilder objectBuilder(Map<String, ? extends JsonValue> map) {
        var bldr = objectBuilder();
        map.forEach(bldr::element);
        return bldr;
    }

    static JsonObjectBuilder objectBuilder(JsonObject from) {
        return objectBuilder(from.members());
    }

    static JsonArrayBuilder arrayBuilder() {
        return new JsonArrayBuilder();
    }

    static JsonArrayBuilder arrayBuilder(JsonArray ja) {
        return arrayBuilder(ja.elements());
    }

    static JsonArrayBuilder arrayBuilder(Collection<JsonValue> elements) {
        var bldr = arrayBuilder();
        elements.forEach(bldr::element);
        return bldr;
    }

    int size();

    sealed interface Builder<T extends Aggregate> {
        int size();

        default boolean isEmpty() {
            return size() == 0;
        }

        T build();

        sealed interface Elem {
            record ValueElem(JsonValue value) implements Elem {}

            record BuilderElem(Builder<?> builder) implements Elem {}
        }
    }

    final class JsonObjectBuilder implements Builder<JsonObject> {

        private JsonObjectBuilder() {
        }

        private final Map<String, Elem> data = new HashMap<>();

        public JsonObjectBuilder named(String name, JsonValue el) {
            data.put(requireNonNull(name), new Elem.ValueElem(requireNonNull(el)));
            return this;
        }

        public JsonObjectBuilder builder(String name, Builder<?> bldr) {
            data.put(name, new Elem.BuilderElem(bldr));
            return this;
        }

        public JsonObjectBuilder basic(String name, Object o) {
            return named(name, Basic.of(o));
        }

        public JsonObjectBuilder element(String name, Object o) {
            return switch (o) {
                case Builder<?> bldr -> builder(name, bldr);
                case JsonValue v -> named(name, v);
                case null, default -> throw new AssertionError();
            };
        }

        public JsonObjectBuilder update(Map<String, ? extends JsonValue> map) {
            map.entrySet().stream()
                    .filter(in(data.keySet(), Map.Entry::getKey))
                    .forEach(e -> element(e.getKey(), e.getValue()));
            return this;
        }

        public JsonObjectBuilder update(JsonObject o) {
            return update(o.members());
        }

        public JsonObjectBuilder merge(JsonObject o) {
            o.members().forEach((key, value) -> data.put(key, switch (value) {
                case JsonObject jo -> new Elem.BuilderElem(objectBuilder(jo));
                case JsonArray ja -> new Elem.BuilderElem(arrayBuilder(ja));
                default -> new Elem.ValueElem(value);
            }));
            return this;
        }

        public JsonObjectBuilder insert(Map<String, ? extends JsonValue> map) {
            map.entrySet().stream()
                    .filter(not(in(data.keySet(), Map.Entry::getKey)))
                    .forEach(e -> named(e.getKey(), e.getValue()));
            return this;
        }

        public JsonObjectBuilder insert(JsonObject o) {
            return insert(o.members());
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
            return new JsonObject(data.entrySet()
                    .stream()
                    .collect(toUnmodifiableMap(Map.Entry::getKey, e -> switch (e.getValue()) {
                        case Elem.BuilderElem(Builder<?> b) -> b.build();
                        case Elem.ValueElem(JsonValue v) -> v;
                    })));
        }

        @Override
        public int size() {
            return data.size();
        }

        public JsonObjectBuilder namedNull(String name) {
            return named(name, JsonNull.INSTANCE);
        }
    }

    final class JsonArrayBuilder implements Builder<JsonArray> {
        private JsonArrayBuilder() {}

        public int size() {
            return data.size();
        }

        @Override
        public JsonArray build() {
            return new JsonArray(data.stream()
                    .map(e -> switch (e) {
                        case Elem.BuilderElem(Builder<?> b) -> b.build();
                        case Elem.ValueElem(JsonValue v) -> v;
                    })
                    .toList());
        }

        private final List<Elem> data = new ArrayList<>();

        public JsonArrayBuilder item(JsonValue elem) {
            data.add(new Elem.ValueElem(requireNonNull(elem)));
            return this;
        }

        public JsonArrayBuilder builder(Builder<?> bldr) {
            data.add(new Elem.BuilderElem(bldr));
            return this;
        }

        public JsonArrayBuilder basic(Object o) {
            return item(Basic.of(o));
        }

        public JsonArrayBuilder element(Object o) {
            return switch (o) {
                case Builder<?> b -> builder(b);
                case null, default -> item(JsonValue.of(o));
            };
        }

        public JsonArrayBuilder nullItem() {
            return item(JsonNull.INSTANCE);
        }
    }
}
