package io.github.ralfspoeth.json.data;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * The aggregate types in the JSON hierarchy, which are JsonArray and JsonObject.
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

    @Override
    default Optional<Boolean> booleanValue() {
        return Optional.empty();
    }

    @Override
    default Optional<BigDecimal> decimalValue() {
        return Optional.empty();
    }

    @Override
    default OptionalDouble doubleValue() {
        return OptionalDouble.empty();
    }

    @Override
    default OptionalInt intValue() {
        return OptionalInt.empty();
    }

    @Override
    default OptionalLong longValue() {
        return OptionalLong.empty();
    }

    @Override
    default Optional<String> stringValue() {
        return Optional.empty();
    }
}
