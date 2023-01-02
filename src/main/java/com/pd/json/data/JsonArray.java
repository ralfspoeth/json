package com.pd.json.data;

import java.util.List;

public record JsonArray(List<JsonElement> elements) implements JsonElement {
}
