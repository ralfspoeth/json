package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.data.*;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static io.github.ralfspoeth.basix.fn.Predicates.eq;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;


/**
 * The {@code Queries} class provides two different types of functions:
 * the {@link #asObject(JsonValue)} function which peals the most natural representation
 * out of a JSON instance, and the {@code {int|long|double}Array(JsonValue)} functions
 * which turn a {@link JsonArray} or {@link JsonNumber}s into an array of primitives
 * {@code int}, {@code long}, or {@code double}.
 */
public class Queries {

    // prevent instantiation
    private Queries() {}

    /**
     * Provides the most natural mapping of a JSON element
     * to their Java counterparts.
     * A {@link JsonObject object} is basically converted into its map of
     * {@link JsonObject#members() members},
     * the values of which are passed to this method recursively.
     * An {@link JsonArray array} is represented by a {@link List}
     * with this function applied to all
     * its {@link JsonArray#elements() elements}.
     * All other {@link Basic} elements are converted using the basic's
     * {@link Basic#value()} function.
     * {@link JsonNull} instances in {@link Aggregate}s are suppressed.
     *
     * @param elem a JSON element, may not be {@code null}
     * @return either a {@link Map}, a {@link List}
     * or a {@code String}, {@code BigDecimal}, {@code Boolean}, or {@code null}
     */
    public static Object asObject(JsonValue elem) {
        return switch (requireNonNull(elem)) {
            case JsonObject(var members) -> asMap(members);
            case JsonArray(var elements) -> asList(elements);
            case Basic<?> basic -> basic.value();
        };
    }

    private static Map<String, ?> asMap(Map<String, JsonValue> members) {
        return members.entrySet().stream()
                .filter(not(eq(JsonNull.INSTANCE, Map.Entry::getValue)))
                .collect(toMap(Map.Entry::getKey, e -> asObject(e.getValue())));
    }

    // turns a JsonArray into a list
    private static List<?> asList(List<JsonValue> elements) {
        return elements.stream()
                .filter(not(eq(JsonNull.INSTANCE, identity())))
                .map(Queries::asObject)
                .toList();
    }

    /**
     * Convert a {@link JsonArray} of {@link JsonNumber}s into an array
     * of {@code int}s.
     * If the
     * @param elem, may be {@code null}
     * @return an array of {@code int}s; never {@code null}, zero length for elements other than a {@link JsonArray}
     */
    public static int[] intArray(@Nullable JsonValue elem) {
        return switch (elem) {
            case JsonArray(var elements) -> {
                var tmp = new int[elements.size()];
                for (int i = 0; i < tmp.length; i++) {
                    tmp[i] = elem.get(i).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow();
                }
                yield tmp;
            }
            case null, default -> new int[0];
        };
    }

    /**
     * Same as {@link #intArray(JsonValue)} but converting the elements of the
     * {@link JsonArray} into {@code long}s.
     */
    public static long[] longArray(@Nullable JsonValue elem) {
        return switch (elem) {
            case JsonArray(var elements) -> {
                var tmp = new long[elements.size()];
                for (int i = 0; i < tmp.length; i++) {
                    tmp[i] = elem.get(i).flatMap(JsonValue::decimal).map(BigDecimal::longValue).orElseThrow();
                }
                yield tmp;
            }
            case null, default -> new long[0];
        };
    }

    /**
     * Same as {@link #intArray(JsonValue)} but converting the elements of the
     * {@link JsonArray} into {@code double}s.
     */
    public static double[] doubleArray(@Nullable JsonValue elem) {
        return switch (elem) {
            case JsonArray(var elements) -> {
                var tmp = new double[elements.size()];
                for (int i = 0; i < tmp.length; i++) {
                    tmp[i] = elem.get(i).flatMap(JsonValue::decimal).map(BigDecimal::doubleValue).orElseThrow();
                }
                yield tmp;
            }
            case null, default -> new double[0];
        };
    }
}
