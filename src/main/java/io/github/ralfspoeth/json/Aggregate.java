package io.github.ralfspoeth.json;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
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

        default boolean isEmpty() {
            return size()==0;
        }

        T build();
    }

    final class JsonObjectBuilder implements Builder<JsonObject> {

        private JsonObjectBuilder(){}

        private final Map<String, Element> data = new HashMap<>();
        public JsonObjectBuilder named(String name, Element el) {
            data.put(name, el);
            return this;
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
    
    private static JsonArray ofIterable(Iterable<?> iterable) {
        var ab = new JsonArrayBuilder();
        for(var it: iterable) {
            ab.item(Element.of(it));
        }
        return ab.build();
    }

    private static JsonObject ofRecord(Object r) {
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
        for(int i=0; i < Array.getLength(o); i++) {
            ab.item(Element.of(Array.get(o, i)));
        }
        return ab.build();
    }
    static Aggregate of(Object o) {
        return switch(o) {
            case Record r -> ofRecord(r);
            case Iterable<?> it -> ofIterable(it);
            case Object a when a.getClass().isArray() -> ofArray(a);
            case null, default -> throw new IllegalArgumentException("Cannot turn into an aggregate: " + o);
        };
    }
}
