package io.github.ralfspoeth.json;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

/**
 * A JSON object comprised of an immutable map of name-value pairs.
 * The members map is immutable by utilizing {@link Map#copyOf(Map)}.
 *
 * @param members a non-{code null} map of name-value pairs
 */
public record JsonObject(Map<String, JsonValue> members) implements Aggregate, Function<String, @Nullable JsonValue> {

    public JsonObject {
        members = Map.copyOf(members);
    }

    public static JsonObject ofMap(Map<?, ?> map) {
        var members = map.entrySet()
                .stream()
                .collect(toMap(String::valueOf, JsonValue::of));
        return new JsonObject(members);
    }

    @Override
    public boolean test(@Nullable JsonValue jv) {
        return jv instanceof JsonObject(var mems) && mems.size() == members.size() && mems.equals(members);
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

    @Override
    public String json() {
        return members.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + e.getValue().json())
                .collect(joining(", ", "{", "}"));
    }

    @Override
    public Optional<JsonValue> get(String name) {
        return Optional.ofNullable(members.get(name));
    }

    @Override
    public @Nullable JsonValue apply(String name) {
        return members.get(name);
    }
}
