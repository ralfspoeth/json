package io.github.ralfspoeth.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public sealed interface Aggregate extends Element permits JsonArray, JsonObject {
    static JsonObjectBuilder objectBuilder() {
        return new JsonObjectBuilder();
    }

    static JsonArrayBuilder arrayBuilder() {
        return new JsonArrayBuilder();
    }

    int size();
    int depth();

    sealed interface Builder<T extends Aggregate> {
        int size();

        T build();
    }

    final class JsonObjectBuilder implements Builder<JsonObject> {

        private JsonObjectBuilder(){}

        private final Map<String, Element> data = new HashMap<>();
        public JsonObjectBuilder named(String name, Element el) {
            data.put(name, el);
            return this;
        }

        public JsonObjectBuilder named(String name, Builder<?> b) {
            return named(name, b.build());
        }

        public JsonObjectBuilder named(String name, Object o) {
            return named(name, Basic.of(o));
        }

        public JsonObjectBuilder namedNull(String name) {
            return named(name, JsonNull.INSTANCE);
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
        private JsonArrayBuilder(){}

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

        public JsonArrayBuilder item(Builder<?> jb) {
            data.add(jb);
            return this;
        }

        public JsonArrayBuilder item(Object o) {
            data.add(Basic.of(o));
            return this;
        }

        public JsonArrayBuilder nullItem() {
            return item(JsonNull.INSTANCE);
        }
    }
}
