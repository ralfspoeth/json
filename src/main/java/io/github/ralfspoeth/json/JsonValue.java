package io.github.ralfspoeth.json;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * The root interface of the JSON hierarchy.
 * {@code JsonValue} is a sealed interface which allows for two subtypes
 * {@link Basic} and {@link Aggregate}, which are sealed interfaces themselves.
 * All instances are immutable, {@link Predicate}s, have a {@link #depth()}
 * and a {@link #json()} representation.
 * Every value can report its {@code boolean}, {@link BigDecimal decimal} (and all primitive cousins),
 * {@code String string} value or the element at a given index (empty if out of bounds or not an array)
 * and the value of a given key (in an object) if it exists.
 */
public sealed interface JsonValue extends Predicate<@Nullable JsonValue> permits Aggregate, Basic {

    /**
     * The JSON representation of the value.
     * @return a JSON string, never {@code null}, which adheres to the JSON specification.
     */
    String json();

    /**
     * The depth of tree of nested values.
     * The depth of each leaf node is 1.
     * The depth of container nodes is 1 + the maximum depth of its descendants.
     */
    int depth();

    /**
     * Converts an object into a JSON value.
     * If you happen to pass a {@link Builder}, its {@link Builder#build()} method will be invoked;
     * if a JSON value, that itself will be returned - both just as a safeguard.
     * A map is converted into a {@link JsonObject} using {@link JsonObject#ofMap(Map)},
     * arrays and other iterables into {@link JsonArray}s.
     * Everything else is passed to {@link Basic#of(Object)}.
     *
     * @param o an object, may be {@code null}
     * @return a JSON value; never {@code null}
     */
    static JsonValue of(@Nullable Object o) {
        return switch(o) {
            case Builder<?> b -> b.build();
            case JsonValue v -> v;
            case Map<?, ?> m -> JsonObject.ofMap(m);
            case Iterable<?> it -> JsonArray.ofIterable(it);
            case Object array when array.getClass().isArray() -> JsonArray.ofArray(array);
            case null, default -> Basic.of(o);
        };
    }

    /**
     * See {@link #equals(Object)}
     */
    @Override
    int hashCode();

    /**
     * Both methods {@code equals(Object)} and {@link #hashCode} need to be overwritten
     * such that for two {@code JsonValue} instances {@code a} and {@code b}:
     * <ul>
     *     <li>{@code a.getClass()==b.getClass()}</li>
     *     <li>for numerical values in {@code JsonNumber}s: numerical equality</li>
     *     <li>for all other basic values that {@code a.value().equals(b.value())}</li>
     *     <li>for arrays that the contained list of elements equal in order and by value,
     *         or {@code a.elements().size()==b.elements().size()} and for each {@code 0<= i < a.elements().size()}
     *         {@code a.elements().get(i).equals(b.elements().get(i))}
     *     </li>
     *     <li>for objects that they contain the same set of keys
     *         {@code a.members().keySet().containsAll(b.members().keySet()) && b.members().keySet().containsAll(a.members().keySet())}
     *         and the equality of the associated values, such that {@code a.members().get(k).equals(b.members().get(k))} for each {@code k}
     *     </li>
     * </ul>
     * See {@link Object#equals(Object)} and {@link Object#hashCode()}
     */
    @Override
    boolean equals(Object o);

    Optional<Boolean> booleanValue();

    default boolean booleanValue(boolean def) {
        return booleanValue().orElse(def);
    }

    default OptionalInt intValue() {
        return decimalValue()
                .map(dv -> OptionalInt.of(dv.intValue()))
                .orElse(OptionalInt.empty());
    }

    /**
     * Same as {@code intValue().orElse(def)}.
     */
    default int intValue(int def) {
        return intValue().orElse(def);
    }

    default OptionalLong longValue() {
        return decimalValue()
                .map(dv -> OptionalLong.of(dv.longValue()))
                .orElse(OptionalLong.empty());
    }

    /**
     * Same as {@code longValue().orElse(def)}.
     */
    default long longValue(long def) {
        return longValue().orElse(def);
    }

    default OptionalDouble doubleValue() {
        return decimalValue()
                .map(dv -> OptionalDouble.of(dv.doubleValue()))
                .orElse(OptionalDouble.empty());
    }
    /**
     * Same as {@code doubleValue().orElse(def)}.
     */
    default double doubleValue(double def) {
        return doubleValue().orElse(def);
    }

    Optional<BigDecimal> decimalValue();

    /**
     * Same as {@code decimalValue().orElse(def)}.
     */
    default BigDecimal decimalValue(BigDecimal def) {
        return decimalValue().orElse(requireNonNull(def));
    }

    Optional<String> stringValue();

    /**
     * Same as {@code stringValue().orElse(def)}.
     */
    default String stringValue(String def) {
        return stringValue().orElse(requireNonNull(def));
    }

    /**
     * Get the value at a given index in a JSON array,
     * or an empty optional if the index is out of bounds or
     * this is not an array.
     * @param index the index
     * @return the value at the given index, or an empty optional
     */
    default Optional<JsonValue> get(int index) {
        return Optional.empty();
    }

    /**
     * Get the member named "name" in this JSON object;
     * or an empty optional if this is not a JSON object or
     * there is no such member.
     * @param name the name
     * @return the value of the member, or an empty optional
     */
    default Optional<JsonValue> get(String name) {
        return Optional.empty();
    }

    /**
     * Get all the elements of this it is a JSON array,
     * or an empty list if this is not an array.
     */
    default List<JsonValue> elements() {
        return List.of();
    }

    /**
     * Get all the members of this it is a JSON object,
     * or an empty map if this is not JSON object.
     */
    default Map<String, JsonValue> members() {
        return Map.of();
    }
}
