package com.pd.json.data;

public sealed interface JsonElement permits JsonArray, JsonObject, JsonValue {
}
