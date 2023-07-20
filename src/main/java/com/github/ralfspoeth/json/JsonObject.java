package com.github.ralfspoeth.json;

import java.util.Map;

import static java.util.Optional.ofNullable;

public record JsonObject(Map<String, JsonElement> members) implements JsonAggregate {
    public JsonObject {
        members = Map.copyOf(members);
    }

    @Override
    public int size() {
        return members.size();
    }

    public <T extends JsonElement> T get(String name, Class<T> cls) {
        return ofNullable(members.get(name)).map(cls::cast).orElse(null);
    }

    public JsonElement get(String name) {
        return members.get(name);
    }
}
