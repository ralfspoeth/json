package io.github.ralfspoeth.json;

import java.lang.reflect.Array;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.Aggregate.arrayBuilder;
import static java.util.Objects.requireNonNullElse;

public record JsonArray(List<JsonValue> elements) implements Aggregate, IntFunction<JsonValue> {
    public JsonArray {
        elements = List.copyOf(requireNonNullElse(elements, List.of()));
    }

    public JsonArray() {
        this(List.of());
    }

    public static JsonArray of(Object o) {
        return switch(o) {
            case Iterable<?> it -> ofIterable(it);
            case Object ar when ar.getClass().isArray() -> ofArray(ar);
            case null -> new JsonArray(List.of());
            default -> throw new IllegalArgumentException(
                    "Conversion of %s into %s not supported".formatted(o, JsonArray.class)
            );
        };
    }

    public static JsonArray ofIterable(Iterable<?> iterable) {
        var ab = arrayBuilder();
        for (var it : iterable) {
            ab.item(JsonValue.of(it));
        }
        return ab.build();
    }

    public static JsonArray ofArray(Object o) {
        var ab = arrayBuilder();
        for (int i = 0, len = Array.getLength(o); i < len; i++) {
            ab.item(JsonValue.of(Array.get(o, i)));
        }
        return ab.build();
    }

    @Override
    public String json() {
        return elements.stream()
                .map(JsonValue::json)
                .collect(Collectors.joining(",", "[", "]"));
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public int depth() {
        return elements.stream().mapToInt(JsonValue::depth).max().orElse(0) + 1;
    }

    public Stream<JsonValue> stream() {
        return elements.stream();
    }

    @Override
    public JsonValue apply(int index) {
        return elements.get(index);
    }
}
