package com.github.ralfspoeth.json.builder;

import com.github.ralfspoeth.json.data.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract sealed class JsonBuilder<T extends JsonElement> {

    public static final class JsonObjectBuilder extends JsonBuilder<JsonObject> {

        private JsonObjectBuilder(){}

        private final Map<String, Object> data = new HashMap<>();
        public JsonObjectBuilder named(String name, JsonElement el) {
            data.put(name, el);
            return this;
        }

        public JsonObjectBuilder named(String name, JsonBuilder<?> b) {
            data.put(name, b);
            return this;
        }

        public JsonObjectBuilder named(String name, Object o) {
            data.put(name, JsonValue.of(o));
            return this;
        }

        public JsonObjectBuilder namedNull(String name) {
            data.put(name, JsonNull.INSTANCE);
            return this;
        }

        @Override
        public JsonObject build() {
            var tmp = data.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e-> switch (e.getValue()) {
                        case JsonElement je -> je;
                        case JsonBuilder<?> ob -> ob.build();
                        default -> throw new AssertionError(e.getValue().getClass());
                    }
            ));
            return new JsonObject(tmp);
        }
    }

    public static final class JsonArrayBuilder extends JsonBuilder<JsonArray> {
        private JsonArrayBuilder(){}

        @Override
        public JsonArray build() {
            return new JsonArray(
                    data.stream().map(item -> switch (item) {
                        case JsonElement je -> je;
                        case JsonBuilder<?> jb -> jb.build();
                        default -> throw new AssertionError();
                    }).toList()
            );
        }

        private final List<Object> data = new ArrayList<>();

        public JsonArrayBuilder item(JsonElement elem) {
            data.add(elem);
            return this;
        }

        public JsonArrayBuilder item(JsonBuilder<?> jb) {
            data.add(jb);
            return this;
        }

        public JsonArrayBuilder item(Object o) {
            data.add(JsonValue.of(o));
            return this;
        }

        public JsonArrayBuilder nullItem() {
            return item(JsonNull.INSTANCE);
        }
    }

    public abstract T build();

    public static JsonObjectBuilder objectBuilder() {
        return new JsonObjectBuilder();
    }

    public static JsonArrayBuilder arrayBuilder() {
        return new JsonArrayBuilder();
    }
}
