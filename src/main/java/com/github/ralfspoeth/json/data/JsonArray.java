package com.github.ralfspoeth.json.data;

import java.util.List;

public record JsonArray(List<JsonElement> elements) implements JsonElement {
    public JsonArray {
        elements = List.copyOf(elements);
    }

    public JsonObject getObject(int index) {
        return (JsonObject) elements.get(index);
    }

    public JsonArray getArray(int index) {
        return (JsonArray) elements.get(index);
    }

    public JsonValue getValue(int index) {
        return (JsonValue) elements.get(index);
    }
}
