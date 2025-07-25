package io.github.ralfspoeth.json;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.function.Function;

import static io.github.ralfspoeth.json.Aggregate.objectBuilder;
import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

public record JsonObject(Map<String, JsonValue> members) implements Aggregate, Function<String, JsonValue> {

    public JsonObject {
        members = Map.copyOf(requireNonNullElse(members, Map.of()));
    }

    public JsonObject() {
        this(Map.of());
    }

    public static <R extends Record> JsonObject ofRecord(R r) {
        var rc = r.getClass().getRecordComponents();
        var ob = objectBuilder();
        for (RecordComponent comp : rc) {
            var name = comp.getName();
            try {
                var value = comp.getAccessor().invoke(r);
                ob.named(name, JsonValue.of(value));
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        return ob.build();
    }

    public static JsonObject ofMap(Map<?, ?> map) {
        var members = map.entrySet()
                .stream()
                .collect(toMap(String::valueOf, JsonValue::of));
        return new JsonObject(members);
    }

    @Override
    public boolean test(JsonValue jv) {
        return switch (jv) {
            case JsonObject(var mems) -> mems.equals(members);
            case null, default -> false;
        };
    }

    @Override
    public int size() {
        return members.size();
    }

    @Override
    public int depth() {
        return members.values()
                .stream()
                .mapToInt(JsonValue::depth)
                .max()
                .orElse(0) + 1;
    }

    public <T extends JsonValue> T get(String name, Class<T> cls) {
        return ofNullable(members.get(name)).map(cls::cast).orElse(null);
    }

    @Override
    public String json() {
        return members.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + e.getValue().json())
                .collect(joining(", ", "{", "}"));
    }

    @Override
    public JsonValue apply(String name) {
        return get(name, JsonValue.class);
    }
}
