package io.github.ralfspoeth.json.data;

/**
 * The aggregate types in the JSON hierarchy,
 * which are {@link JsonArray} and {@link JsonObject}.
 */
public sealed interface Aggregate extends JsonValue permits JsonArray, JsonObject {

    /**
     * The number of elements or members, respectively, in the aggregate.
     */
    int size();

    /**
     * {@code true} when the elements or members, respectively, are empty.
     */
    default boolean isEmpty() {
        return size() == 0;
    }
}
