package io.github.ralfspoeth.json;

import java.util.List;
import java.util.function.IntFunction;

public record JsonArray(List<Element> elements) implements Aggregate, IntFunction<Element> {
    public JsonArray {
        elements = List.copyOf(elements);
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public int depth() {
        return 1;
    }


    @Override
    public Element apply(int index) {
        return elements.get(index);
    }
}
