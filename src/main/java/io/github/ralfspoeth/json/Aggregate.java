package io.github.ralfspoeth.json;

public sealed interface Aggregate extends Element permits JsonArray, JsonObject {
    int size();
    int depth();
}
