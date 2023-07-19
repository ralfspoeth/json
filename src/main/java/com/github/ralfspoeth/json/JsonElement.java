package com.github.ralfspoeth.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public sealed interface JsonElement permits JsonArray, JsonObject, JsonValue {
    static JsonObjectBuilder objectBuilder() {
        return new JsonObjectBuilder();
    }

    static JsonArrayBuilder arrayBuilder() {
        return new JsonArrayBuilder();
    }

    sealed interface Builder<T extends JsonElement> {
        int size();

        T build();
    }

    final class JsonObjectBuilder implements Builder<JsonObject> {

        private JsonObjectBuilder(){}

        private final Map<String, JsonElement> data = new HashMap<>();
        public JsonElement.JsonObjectBuilder named(String name, JsonElement el) {
            data.put(name, el);
            return this;
        }

        public JsonElement.JsonObjectBuilder named(String name, Builder<?> b) {
            return named(name, b.build());
        }

        public JsonElement.JsonObjectBuilder named(String name, Object o) {
            return named(name, JsonValue.of(o));
        }

        public JsonElement.JsonObjectBuilder namedNull(String name) {
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
                        case JsonElement je -> je;
                        case Builder<?> jb -> jb.build();
                        default -> throw new AssertionError();
                    }).toList()
            );
        }

        private final List<Object> data = new ArrayList<>();

        public JsonElement.JsonArrayBuilder item(JsonElement elem) {
            data.add(elem);
            return this;
        }

        public JsonElement.JsonArrayBuilder item(Builder<?> jb) {
            data.add(jb);
            return this;
        }

        public JsonElement.JsonArrayBuilder item(Object o) {
            data.add(JsonValue.of(o));
            return this;
        }

        public JsonElement.JsonArrayBuilder nullItem() {
            return item(JsonNull.INSTANCE);
        }
    }

    record JsonValueBuilder(JsonValue value) implements Builder<JsonValue> {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public JsonValue build() {
            return value;
        }
    }
}
