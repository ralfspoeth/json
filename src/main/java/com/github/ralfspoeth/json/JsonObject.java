package com.github.ralfspoeth.json;

import java.util.Map;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

public record JsonObject(Map<String, Element> members) implements Aggregate, Function<String, Element> {
    public JsonObject {
        members = Map.copyOf(members);
    }

    @Override
    public int size() {
        return members.size();
    }

    public <T extends Element> T get(String name, Class<T> cls) {
        return ofNullable(members.get(name)).map(cls::cast).orElse(null);
    }

    @Override
    public Element apply(String name) {
        return members.get(name);
    }
}
