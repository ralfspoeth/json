package io.github.ralfspoeth.json.query;

import static io.github.ralfspoeth.basix.fn.Predicates.eq;
import static io.github.ralfspoeth.json.JsonBoolean.FALSE;
import static io.github.ralfspoeth.json.JsonBoolean.TRUE;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;

import io.github.ralfspoeth.json.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

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
    public static Object value(JsonValue elem) {
        return switch (requireNonNull(elem)) {
            case JsonObject jo -> asMap(jo);
            case JsonArray ja -> asList(ja);
            case Basic<?> basic -> asBasic(basic);
        };
    }

    private static Map<String, ?> asMap(JsonValue elem) {
        return switch (elem) {
            case JsonObject jo -> jo
                .members()
                .entrySet()
                .stream()
                .filter(not(eq(JsonNull.INSTANCE, Map.Entry::getValue)))
                .collect(toMap(Map.Entry::getKey, e -> value(e.getValue())));
            case null, default -> throw new IllegalArgumentException(
                elem + " is not a JSON Object"
            );
        };
    }

    // turns a basic into an object
    private static Object asBasic(JsonValue elem) {
        return switch (elem) {
            case Basic<?> basic -> basic.value();
            case null, default -> throw new IllegalArgumentException(
                elem + " is not a basic JSON element"
            );
        };
    }

    // turns a JsonArray into a list
    private static List<?> asList(JsonValue elem) {
        return switch (elem) {
            case JsonArray ar -> ar
                .elements()
                .stream()
                .filter(not(eq(JsonNull.INSTANCE, identity())))
                .map(Queries::value)
                .toList();
            case null, default -> throw new IllegalArgumentException(
                elem + " is not a JSON array"
            );
        };
    }

    /**
     * Exactly the same as {@link #value(JsonValue)} except
     * that it allows {@code null} as parameter 1.
     *
     * @param elem a JSON element, may be null
     * @param def  the default value if {@code elem} is {@code null}
     * @return as in {@link #value(JsonValue)}
     */
    public static Object value(JsonValue elem, Object def) {
        return ofNullable(elem).map(Queries::value).orElse(def);
    }

    /**
     * If the {@link JsonValue} passed in is a {@link JsonObject} then
     * return its {@link JsonObject#members() members}, otherwise an empty {@link Map}.
     *
     * @param elem an element, may be {@code null}.
     * @return the members of a JsonObject, or an empty map
     */
    public static Map<String, JsonValue> members(JsonValue elem) {
        return switch (elem) {
            case JsonObject(var members) -> members;
            case null, default -> Map.of();
        };
    }

    public static Optional<JsonValue> member(JsonValue elem, String key) {
        return ofNullable(members(elem).get(requireNonNull(key)));
    }

    /**
     * Shortcut to {@link #members(JsonValue)} followed by
     * {@link Map#get(Object)} with the non-null member name.
     *
     * @param elem an arbitrary JSON element, maybe {@code null}
     * @param name a member name, may not be {@code null}
     * @return the member of the element given it is a {@link JsonObject}
     */
    public static JsonValue get(JsonValue elem, String name) {
        return members(elem).get(requireNonNull(name));
    }

    /**
     * If the {@link JsonValue} passed in is a {@link JsonArray} then
     * return its {@link JsonArray#elements() elements}, otherwise an empty {@link List}.
     *
     * @param elem an element, may be {@code null}
     * @return the elements of a JsonArray, or an empty list
     */
    public static List<JsonValue> elements(JsonValue elem) {
        return switch (elem) {
            case JsonArray(var list) -> list;
            case null, default -> List.of();
        };
    }

    /**
     * Converts an {@link JsonValue element} to {@code int}.
     * <p>
     * The result is:
     * <ul>
     *     <li>(int){@link JsonNumber#numVal()}</li>
     *     <li>{@link JsonBoolean#TRUE}: 1</li>
     *     <li>{@link JsonBoolean#FALSE}: 0</li>
     *     <li>{@link JsonNull}: 0</li>
     *     <li>{@link Integer#parseInt(String)} of {@link JsonString#value()}</li>
     * </ul>
     *
     * @param elem an Element
     * @return an int value
     */
    public static int intValue(JsonValue elem, int def) {
        return switch (elem) {
            case null -> def;
            case JsonNumber n -> n.numVal().intValue();
            case TRUE -> 1;
            case FALSE -> 0;
            case JsonString s -> Integer.parseInt(s.value());
            case JsonNull ignored -> 0;
            case Aggregate a -> throw new IllegalArgumentException(
                "cannot convert to int: " + a
            );
        };
    }

    /**
     * Same as {@link #intValue(JsonValue, int)} with a default value of 0.
     */
    public static int intValue(JsonValue elem) {
        return intValue(requireNonNull(elem), 0);
    }

    /**
     * Same as {@link #intValue(JsonValue, int)} except that it converts the result to
     * {code long}.
     */
    public static long longValue(JsonValue elem, long def) {
        return switch (elem) {
            case null -> def;
            case JsonNumber n -> n.numVal().longValue();
            case TRUE -> 1L;
            case FALSE -> 0L;
            case JsonString s -> Long.parseLong(s.value());
            case JsonNull ignored -> 0L;
            case Aggregate a -> throw new IllegalArgumentException(
                "cannot convert to long: " + a
            );
        };
    }

    /**
     * Same as {@link #longValue(JsonValue, long)}
     * with a default value of 0L.
     */
    public static long longValue(JsonValue elem) {
        return longValue(requireNonNull(elem), 0);
    }

    /**
     * Same as {@link #intValue(JsonValue, int)} except that it converts
     * the result to {@code double} and strings are parsed using {@link Double#parseDouble(String)}.
     */
    public static double doubleValue(JsonValue elem, double def) {
        return switch (elem) {
            case null -> def;
            case JsonNumber n -> n.numVal().doubleValue();
            case TRUE -> 1d;
            case FALSE -> 0d;
            case JsonString s -> Double.parseDouble(s.value());
            case JsonNull ignored -> 0d;
            case Aggregate a -> throw new IllegalArgumentException(
                "cannot convert to double: " + a
            );
        };
    }

    /**
     * {@link #doubleValue(JsonValue, double)} with a default value of 0d.
     */
    public static double doubleValue(JsonValue elem) {
        return doubleValue(requireNonNull(elem), 0d);
    }

    /**
     * Same as {@link #intValue(JsonValue, int)} except that it converts the result to
     * {@link BigDecimal}.
     */
    public static BigDecimal decimalValue(JsonValue elem, BigDecimal def) {
        return switch (elem) {
            case null -> def;
            case JsonNumber n -> n.numVal();
            case TRUE -> BigDecimal.ONE;
            case FALSE -> BigDecimal.ZERO;
            case JsonString s -> new BigDecimal(s.value());
            case JsonNull ignored -> BigDecimal.ZERO;
            case Aggregate a -> throw new IllegalArgumentException(
                "cannot convert to BigDecimal: " + a
            );
        };
    }

    public static BigDecimal decimalValue(JsonValue elem) {
        return decimalValue(requireNonNull(elem), BigDecimal.ZERO);
    }

    /**
     * Converts {@link JsonString}s to enum values using
     * {@link Enum#valueOf(Class, String)}.
     *
     * @param enumClass the parameterized enum class
     * @param elem      the element to convert, may be {@code null}
     * @param def       the default value
     * @param <E>       the enum type
     * @return the enum value
     * @throws IllegalArgumentException if elem not a {@link JsonString} or {@link JsonNull}.
     */
    public static <E extends Enum<E>> E enumValue(
        Class<E> enumClass,
        JsonValue elem,
        E def
    ) {
        return switch (elem) {
            case null -> def;
            case JsonNull ignored -> def;
            case JsonString js -> Enum.valueOf(enumClass, js.value());
            default -> throw new IllegalArgumentException(
                "cannot convert to enum: " + elem
            );
        };
    }

    /**
     * Same as {@code enumValue} with a default value of {@code null}.
     */
    public static <E extends Enum<E>> E enumValue(
        Class<E> enumClass,
        JsonValue elem
    ) {
        return enumValue(enumClass, elem, (E) null);
    }

    /**
     * Converts a {@link JsonString} to an {@link Enum} instance ignoring the case of the string.
     * {@snippet :
     * // given
     * enum E{a, b}
     * // when
     * var s = new JsonString("A");
     * // then
     * assert enumValueIgnoreCase(E.class, s) == E.a;
     *}
     *
     * @param enumClass the parameterized enum class
     * @param elem      the element to convert
     * @param <E>       the enum type
     * @return the enum value
     */
    public static <E extends Enum<E>> E enumValueIgnoreCase(
        Class<E> enumClass,
        JsonValue elem
    ) {
        if (elem instanceof JsonString(String value)) {
            return stream(enumClass.getEnumConstants())
                .collect(toMap(c -> c.name().toUpperCase(), identity()))
                .get(value.toUpperCase());
        } else {
            throw new IllegalArgumentException(
                "cannot convert to enum: " + elem
            );
        }
    }

    /**
     * Converts an {@link JsonValue} to an {@link Enum} instance by first extracting
     * a string value using the {@code extractor} function and then through {@link Enum#valueOf(Class, String)}.
     *
     * @param enumClass the parameterized enum class
     * @param elem      the element to convert
     * @param extractor an extractor function
     * @param <E>       the enum type
     * @return the enum value
     */
    public static <E extends Enum<E>> E enumValue(
        Class<E> enumClass,
        JsonValue elem,
        Function<JsonValue, String> extractor
    ) {
        return extractor.andThen(s -> Enum.valueOf(enumClass, s)).apply(elem);
    }

    /**
     * Convert an {link Element} to a {@link String} value.
     * Conversion rules:
     * <ul>
     *     <li>JsonString: {@link JsonString#value()}</li>
     *     <li>JsonNull: "null"</li>
     *     <li>JsonNumber: {@link Double#toString(double)}</li>
     *     <li>JsonBoolean: {@link Boolean#toString(boolean)}</li>
     *     <li>JsonArray: {@link JsonArray#elements()}</li>
     *     <li>JsonObject: {@link JsonObject#members()}</li>
     * </ul>
     *
     * @param elem the element to convert
     * @param def  the default value
     * @return the string value
     */
    public static String stringValue(JsonValue elem, String def) {
        return switch (elem) {
            case null -> def;
            case JsonString s -> s.value();
            case JsonNull ignored -> "null";
            case JsonNumber n -> n.numVal().toString();
            case JsonBoolean b -> Boolean.toString(b == TRUE);
            case JsonArray a -> a.elements().toString();
            case JsonObject o -> o.members().toString();
        };
    }

    /**
     * {@link #stringValue(JsonValue, String)} with a default value of {@code null}.
     */
    public static String stringValue(JsonValue elem) {
        return stringValue(requireNonNull(elem), null);
    }

    /**
     * Convert an {link Element} to a {@code boolean} value.
     * String values are parsed using {link Boolean#parseBoolean(String)},
     * numbers are compared to 0d (where 0d is false) and {@link JsonBoolean} as converted
     * naturally.
     *
     * @param elem the element to convert
     * @param def  the default value
     * @return the boolean value
     */
    public static boolean booleanValue(JsonValue elem, boolean def) {
        return switch (elem) {
            case null -> def;
            case JsonBoolean b -> b == TRUE;
            case JsonString(String value) -> Boolean.parseBoolean(value);
            case JsonNumber jn -> jn.numVal().compareTo(BigDecimal.ZERO) != 0;
            default -> throw new IllegalArgumentException(
                "cannot convert to boolean: " + elem
            );
        };
    }

    /**
     * {@link #booleanValue(JsonValue, boolean)} with a default value of {@code false}.
     */
    public static boolean booleanValue(JsonValue elem) {
        return booleanValue(requireNonNull(elem), false);
    }

    private static int[] intArray(JsonArray ja) {
        var tmp = new int[ja.size()];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = intValue(ja.elements().get(i));
        }
        return tmp;
    }

    /**
     * Same as {@link #doubleArray(JsonValue)} but using
     * {@link #intValue(JsonValue)} to convert the elements.
     */
    public static int[] intArray(JsonValue elem) {
        return switch (elem) {
            case JsonArray ja -> intArray(ja);
            case null, default -> new int[0];
        };
    }

    private static long[] longArray(JsonArray ja) {
        var tmp = new long[ja.size()];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = longValue(ja.elements().get(i));
        }
        return tmp;
    }

    /**
     * Same as {@link #doubleArray(JsonValue)} but using
     * {@link #longValue(JsonValue)} to convert the elements.
     */
    public static long[] longArray(JsonValue elem) {
        return switch (elem) {
            case JsonArray ja -> longArray(ja);
            case null, default -> new long[0];
        };
    }

    private static char[] charArray(JsonArray ja) {
        var tmp = new char[ja.size()];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = (char) intValue(ja.elements().get(i));
        }
        return tmp;
    }

    /**
     * Same as {@link #intArray(JsonValue)} but casting the elements
     * to {@code char}.
     */
    public static char[] charArray(JsonValue elem) {
        return switch (elem) {
            case JsonArray ja -> charArray(ja);
            case null, default -> new char[0];
        };
    }

    private static short[] shortArray(JsonArray ja) {
        var tmp = new short[ja.size()];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = (short) intValue(ja.elements().get(i));
        }
        return tmp;
    }

    /**
     * Same as {@link #intArray(JsonValue)} but casting the elements
     * to {@code short}.
     */
    public static short[] shortArray(JsonValue elem) {
        return switch (elem) {
            case JsonArray ja -> shortArray(ja);
            case null, default -> new short[0];
        };
    }

    private static byte[] byteArray(JsonArray ja) {
        var tmp = new byte[ja.size()];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = (byte) intValue(ja.elements().get(i));
        }
        return tmp;
    }

    /**
     * Same as {@link #intArray(JsonValue)} but casting the elements
     * to {@code byte}.
     */
    public static byte[] byteArray(JsonValue elem) {
        return switch (elem) {
            case JsonArray ja -> byteArray(ja);
            case null, default -> new byte[0];
        };
    }

    private static double[] doubleArray(JsonArray ja) {
        var tmp = new double[ja.size()];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = doubleValue(ja.elements().get(i));
        }
        return tmp;
    }

    /**
     * Same as {@link #stringArray(JsonArray)} except
     * that it uses {@link #doubleValue(JsonValue)} to convert the
     * member elements.
     */
    public static double[] doubleArray(JsonValue elem) {
        return switch (elem) {
            case JsonArray ja -> doubleArray(ja);
            case null, default -> new double[0];
        };
    }

    private static float[] floatArray(JsonArray ja) {
        var tmp = new float[ja.size()];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = (float) doubleValue(ja.elements().get(i));
        }
        return tmp;
    }

    /**
     * Same as {@link #doubleArray(JsonValue)} but its array
     * elements are cast to {@code float}.
     */
    public static float[] floatArray(JsonValue elem) {
        return switch (elem) {
            case JsonArray ja -> floatArray(ja);
            case null, default -> new float[0];
        };
    }

    private static BigDecimal[] decimalArray(JsonArray ja) {
        var tmp = new BigDecimal[ja.size()];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = decimalValue(ja.elements().get(i));
        }
        return tmp;
    }

    /**
     * Same as {@link #doubleArray(JsonValue)} except that its
     * array consists of {@link BigDecimal}s.
     */
    public static BigDecimal[] decimalArray(JsonValue elem) {
        return switch (elem) {
            case JsonArray ja -> decimalArray(ja);
            case null, default -> new BigDecimal[0];
        };
    }

    private static boolean[] booleanArray(JsonArray ja) {
        var tmp = new boolean[ja.size()];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = booleanValue(ja.elements().get(i));
        }
        return tmp;
    }

    /**
     * Same as {@link #stringArray(JsonValue)} except that
     * it utilizes {@link #booleanValue(JsonValue)}.
     */
    public static boolean[] booleanArray(JsonValue elem) {
        return switch (elem) {
            case JsonArray ja -> booleanArray(ja);
            case null, default -> new boolean[0];
        };
    }

    private static String[] stringArray(JsonArray ja) {
        var tmp = new String[ja.size()];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = stringValue(ja.elements().get(i));
        }
        return tmp;
    }

    /**
     * Convert a {@link JsonArray} into an array of strings
     * applying {@link #stringValue(JsonValue)} to each of its elements,
     * and an empty array for all other elements.
     *
     * @param elem an element, may be {@code null}
     * @return a potentially empty array of strings, never {@code null}
     */
    public static String[] stringArray(JsonValue elem) {
        return switch (elem) {
            case JsonArray ja -> stringArray(ja);
            case null, default -> new String[0];
        };
    }
}
