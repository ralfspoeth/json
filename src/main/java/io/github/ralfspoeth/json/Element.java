package io.github.ralfspoeth.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public sealed interface Element permits Aggregate, Basic {
    static JsonObjectBuilder objectBuilder() {
        return new JsonObjectBuilder();
    }

    static JsonArrayBuilder arrayBuilder() {
        return new JsonArrayBuilder();
    }

    sealed interface Builder<T extends Aggregate> {
        int size();

        T build();
    }

    final class JsonObjectBuilder implements Builder<JsonObject> {

        private JsonObjectBuilder(){}

        private final Map<String, Element> data = new HashMap<>();
        public Element.JsonObjectBuilder named(String name, Element el) {
            data.put(name, el);
            return this;
        }

        public Element.JsonObjectBuilder named(String name, Builder<?> b) {
            return named(name, b.build());
        }

        public Element.JsonObjectBuilder named(String name, Object o) {
            return named(name, Basic.of(o));
        }

        public Element.JsonObjectBuilder namedNull(String name) {
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

        public Element.JsonArrayBuilder item(Element elem) {
            data.add(elem);
            return this;
        }

        public Element.JsonArrayBuilder item(Builder<?> jb) {
            data.add(jb);
            return this;
        }

        public Element.JsonArrayBuilder item(Object o) {
            data.add(Basic.of(o));
            return this;
        }

        public Element.JsonArrayBuilder nullItem() {
            return item(JsonNull.INSTANCE);
        }
    }

}
