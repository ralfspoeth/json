package io.github.ralfspoeth.json;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.function.Function;

import static io.github.ralfspoeth.json.Aggregate.objectBuilder;
import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

public record JsonObject(Map<String, Element> members) implements Aggregate, Function<String, Element> {

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
                ob.named(name, Element.of(value));
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        return ob.build();
    }

    @Deprecated(forRemoval = true)
    public static JsonObject asJsonObject(Map<?, ?> map) {
        var members = map.entrySet()
                .stream()
                .collect(toMap(String::valueOf, Element::of));
        return new JsonObject(members);
    }

    @Override
    public int size() {
        return members.size();
    }

    @Override
    public int depth() {
        return members.values()
                .stream()
                .mapToInt(Element::depth)
                .max()
                .orElse(0) + 1;
    }

    public <T extends Element> T get(String name, Class<T> cls) {
        return ofNullable(members.get(name)).map(cls::cast).orElse(null);
    }

    @Override
    public Element apply(String name) {
        return get(name, Element.class);
    }
}
