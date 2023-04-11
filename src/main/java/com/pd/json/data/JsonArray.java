package com.pd.json.data;

import java.util.List;

public record JsonArray(List<JsonElement> elements) implements JsonElement {
    public JsonArray {
        elements = List.copyOf(elements);
    }
}
