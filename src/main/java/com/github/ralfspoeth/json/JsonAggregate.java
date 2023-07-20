package com.github.ralfspoeth.json;

public sealed interface JsonAggregate extends JsonElement permits JsonArray, JsonObject {
    int size();
}
