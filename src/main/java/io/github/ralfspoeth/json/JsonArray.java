package io.github.ralfspoeth.json;

import java.lang.reflect.Array;
import java.util.List;
import java.util.function.IntFunction;

import static io.github.ralfspoeth.json.Builder.arrayBuilder;
import static java.util.stream.Collectors.joining;

/**
 * The array aggregate type in the JSON hierarchy.
 *
 * @param elements the elements; if {@code null} substituted by an empty list
 */
public record JsonArray(List<JsonValue> elements) implements Aggregate, IntFunction<JsonValue> {
    public JsonArray {
        elements = List.copyOf(elements);
    }

    public static JsonArray of(Object o) {
        return switch(o) {
            case Iterable<?> it -> ofIterable(it);
            case Object ar when ar.getClass().isArray() -> ofArray(ar);
            default -> throw new IllegalArgumentException(
                    "Conversion of %s into %s not supported".formatted(o, JsonArray.class)
            );
        };
    }

    public static JsonArray ofIterable(Iterable<?> iterable) {
        var ab = arrayBuilder();
        for (var it : iterable) {
            ab.add(JsonValue.of(it));
        }
        return ab.build();
    }

    public static JsonArray ofArray(Object o) {
        var ab = arrayBuilder();
        for (int i = 0, len = Array.getLength(o); i < len; i++) {
            ab.add(JsonValue.of(Array.get(o, i)));
        }
        return ab.build();
    }

    @Override
    public boolean test(JsonValue jv) {
        return switch (jv) {
            case JsonArray(var elems) -> elems.equals(elements());
            default -> false;
        };
    }

    @Override
    public String json() {
        return elements.stream()
                .map(JsonValue::json)
                .collect(joining(", ", "[", "]"));
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public int depth() {
        return elements.stream()
                .mapToInt(JsonValue::depth)
                .max()
                .orElse(0) + 1;
    }

    @Override
    public JsonValue apply(int index) {
        return elements.get(index);
    }

    @Override
    public OptionalValue get(int index) {
        if(index<= elements().size()) {
            return OptionalValue.of(elements().get(index));
        } else {
            return OptionalValue.NULL;
        }
    }
}
