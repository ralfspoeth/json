package com.github.ralfspoeth.json;

import java.util.List;

public record JsonArray(List<JsonElement> elements) implements JsonAggregate {
    public JsonArray {
        elements = List.copyOf(elements);
    }

    @Override
    public int size() {
        return elements.size();
    }
}
