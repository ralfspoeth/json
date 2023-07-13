package com.github.ralfspoeth.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public sealed interface JsonElement permits JsonArray, JsonObject, JsonValue {
    static JsonObjectBuilder objectBuilder() {
        return new JsonObjectBuilder();
    }

    static JsonArrayBuilder arrayBuilder() {
        return new JsonArrayBuilder();
    }

    sealed interface Builder<T extends JsonElement> {

        T build();
    }

    final class JsonObjectBuilder implements Builder<JsonObject> {

        private JsonObjectBuilder(){}

        private final Map<String, Object> data = new HashMap<>();
        public JsonElement.JsonObjectBuilder named(String name, JsonElement el) {
            data.put(name, el);
            return this;
        }

        public JsonElement.JsonObjectBuilder named(String name, Builder<?> b) {
            data.put(name, b);
            return this;
        }

        public JsonElement.JsonObjectBuilder named(String name, Object o) {
            data.put(name, JsonValue.of(o));
            return this;
        }

        public JsonElement.JsonObjectBuilder namedNull(String name) {
            data.put(name, JsonNull.INSTANCE);
            return this;
        }

        @Override
        public JsonObject build() {
            var tmp = data.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e-> switch (e.getValue()) {
                        case JsonElement je -> je;
                        case Builder<?> ob -> ob.build();
                        default -> throw new AssertionError(e.getValue().getClass());
                    }
            ));
            return new JsonObject(tmp);
        }
    }

    final class JsonArrayBuilder implements Builder<JsonArray> {
        private JsonArrayBuilder(){}

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

    final class JsonStringBuilder implements Builder<JsonString> {
        private final StringBuilder bldr = new StringBuilder();

        @Override
        public JsonString build() {
            return new JsonString(bldr.toString());
        }
    }
}
