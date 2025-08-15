package io.github.ralfspoeth.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The basic data types in the JSON hierarchy.
 * @param <T> the type of the (immutable) value wrapped in the {@code Basic} instance.
 */
public sealed interface Basic<T> extends JsonValue permits JsonBoolean, JsonNull, JsonNumber, JsonString {

    String json();

    /**
     * The value wrapped inside this instance.
     * @return the value, never {@code null` except for `JsonNull}
     */
    T value();

    /**
     * The depth of a {@code Basic} instance is always 1 since
     * it is considered a leaf node in a JSON structure.
     * @return 1
     */
    @Override
    default int depth() {
        return 1;
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
    default Optional<JsonValue> get(int index) {
        return Optional.empty();
    }

    @Override
    default Optional<JsonValue> get(String name) {
        return Optional.empty();
    }

    @Override
    default Optional<String> stringValue() {
        return Optional.ofNullable(value())
                .map(Object::toString)
                .or(Optional::empty);
    }

    /**
     * Instantiates the "right" Basic representation for a given
     * object {@code o}.
     * The conversions shouldn't provide any surprises:
     * {@code null} is mapped to {@code JsonNull}, {@code boolean}s
     * to {@code JsonBoolean}, all numerical types to {@code JsonNumber}
     * and all others to {@code JsonString} using the object's {@link  Object#toString}
     * method.
     * @param o an object
     * @return a {@code Basic} instance, never {@code null}
     */
    static Basic<?> of(Object o) {
        return switch(o) {
            case null -> JsonNull.INSTANCE;
            case BigDecimal bd -> new JsonNumber(bd);
            case BigInteger bi -> new JsonNumber(new BigDecimal(bi));
            case Byte b -> new JsonNumber(BigDecimal.valueOf(b));
            case Character c -> new JsonNumber(BigDecimal.valueOf(c));
            case Short s -> new JsonNumber(BigDecimal.valueOf(s));
            case Integer i -> new JsonNumber(BigDecimal.valueOf(i));
            case Long l -> new JsonNumber(BigDecimal.valueOf(l));
            case Float f -> new JsonNumber(BigDecimal.valueOf(f));
            case Double d -> new JsonNumber(BigDecimal.valueOf(d));
            case Number n -> new JsonNumber(BigDecimal.valueOf(n.doubleValue()));
            case Boolean b -> JsonBoolean.of(b);
            case String s -> new JsonString(s);
            case Builder<?> b -> switch(b.build()) {
                case Basic<?> bb -> bb;
                default -> throw new IllegalArgumentException("cannot pass an aggregate builder to Basic.of");
            };
            case Basic<?> bb -> bb;
            case Aggregate a -> throw new IllegalArgumentException("cannot pass an aggregate to Basic.of");
            default -> new JsonString(o.toString());
        };
    }
}
