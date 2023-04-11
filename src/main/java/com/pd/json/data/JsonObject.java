package com.pd.json.data;

import java.util.Map;

public record JsonObject(Map<String, JsonElement> members) implements JsonElement {
    public JsonObject {
        members = Map.copyOf(members);
    }
}
