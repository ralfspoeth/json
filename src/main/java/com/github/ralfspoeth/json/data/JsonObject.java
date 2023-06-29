package com.github.ralfspoeth.json.data;

import java.util.Map;

import static java.util.Optional.ofNullable;

public record JsonObject(Map<String, JsonElement> members) implements JsonElement {
    public JsonObject {
        members = Map.copyOf(members);
    }

    public JsonArray getArray(String name) {
        return (JsonArray) members.get(name);
    }

    public JsonObject getObject(String name) {
        return (JsonObject) members.get(name);
    }

    public JsonValue getValue(String name) {
        return (JsonValue) members.get(name);
    }

    public <T extends JsonElement> T get(String name, Class<T> cls) {
        return ofNullable(members.get(name)).map(cls::cast).orElse(null);
    }
}
