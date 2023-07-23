package com.github.ralfspoeth.json;

import java.util.List;

public record JsonArray(List<Element> elements) implements Aggregate {
    public JsonArray {
        elements = List.copyOf(elements);
    }

    @Override
    public int size() {
        return elements.size();
    }
}
