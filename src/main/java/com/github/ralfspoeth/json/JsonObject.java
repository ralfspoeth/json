package com.github.ralfspoeth.json;

import java.util.Map;

import static java.util.Optional.ofNullable;

public record JsonObject(Map<String, Element> members) implements Aggregate {
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

    public Element get(String name) {
        return members.get(name);
    }
}
