package io.github.ralfspoeth.json;

import java.util.*;

import static io.github.ralfspoeth.basix.fn.Predicates.in;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;

public sealed interface Aggregate extends Element permits JsonArray, JsonObject {
    static JsonObjectBuilder objectBuilder() {
        return new JsonObjectBuilder();
    }

    static JsonObjectBuilder objectBuilder(Map<String, ? extends Element> map) {
        var bldr = objectBuilder();
        map.forEach(bldr::named);
        return bldr;
    }

    static JsonObjectBuilder objectBuilder(JsonObject from) {
        return objectBuilder(from.members());
    }

    static JsonArrayBuilder arrayBuilder() {
        return new JsonArrayBuilder();
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

        private JsonObjectBuilder() {}

        private final Map<String, Element> data = new HashMap<>();

        public JsonObjectBuilder named(String name, Element el) {
            data.put(requireNonNull(name), requireNonNull(el));
            return this;
        }

        public JsonObjectBuilder basic(String name, Object o) {
            return named(name, Basic.of(o));
        }

        public JsonObjectBuilder element(String name, Object o) {
            return named(name, Element.of(o));
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

        public JsonObjectBuilder insert(Map<String, ? extends Element> map) {
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
            return new JsonObject(data);
        }

        @Override
        public int size() {
            return data.size();
        }
    }

    final class JsonArrayBuilder implements Builder<JsonArray> {
        private JsonArrayBuilder() {}

        public int size() {
            return data.size();
        }

        @Override
        public JsonArray build() {
            return buildArray();
        }

        public JsonArray buildArray() {
            return new JsonArray(data.stream().toList());
        }

        private final List<Element> data = new ArrayList<>();

        public JsonArrayBuilder item(Element elem) {
            if(data.add(requireNonNull(elem))) return this; else throw new AssertionError();
        }

        public JsonArrayBuilder basic(Object o) {
            return item(Basic.of(o));
        }

        public JsonArrayBuilder element(Object o) {
            return item(Element.of(o));
        }

        public JsonArrayBuilder nullItem() {
            return item(JsonNull.INSTANCE);
        }
    }
}
