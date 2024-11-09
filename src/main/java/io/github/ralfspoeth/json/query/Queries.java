package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.*;

import java.util.*;
import java.util.function.Function;

import static io.github.ralfspoeth.basix.fn.Predicates.eq;
import static io.github.ralfspoeth.json.JsonBoolean.FALSE;
import static io.github.ralfspoeth.json.JsonBoolean.TRUE;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;


public class Queries {

    private Queries() {
        // prevent instantiation
    }

    private static Map<String, ?> asMap(Element elem) {
        return switch (elem) {
            case JsonObject jo -> jo.members()
                    .entrySet()
                    .stream()
                    .filter(not(eq(JsonNull.INSTANCE, Map.Entry::getValue)))
                    .collect(toMap(Map.Entry::getKey, e -> value(e.getValue())));
            case null, default -> throw new IllegalArgumentException(elem + " is not a JSON Object");
        };
    }

    // turns a basic into an object
    private static Object asBasic(Element elem) {
        return switch (elem) {
            case Basic<?> basic -> basic.value();
            case null, default -> throw new IllegalArgumentException(elem + " is not a basic JSON element");
        };
    }

    // turns a JsonArray into a list
    private static List<?> asList(Element elem) {
        return switch (elem) {
            case JsonArray ar -> ar.elements()
                    .stream()
                    .filter(not(eq(JsonNull.INSTANCE, identity())))
                    .map(Queries::value)
                    .toList();
            case null, default -> throw new IllegalArgumentException(elem + " is not a JSON array");
        };
    }

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
     *
     * @param elem a JSON element, may not be {@code null}
     * @return either a {@link Map}, a {@link List}
     * or a {@code String}, {@code Double}, {@code Boolean}, or {@code null}
     */
    public static Object value(Element elem) {
        return switch (requireNonNull(elem)) {
            case JsonObject jo -> asMap(jo);
            case JsonArray ja -> asList(ja);
            case Basic<?> basic -> asBasic(basic);
        };
    }

    /**
     * Exactly the same as {@link #value(Element)} except
     * that it allows {@code null} as parameter 1.
     *
     * @param elem a JSON element, may be null
     * @param def  the default value if {@code elem} is {@code null}
     * @return as in {@link #value(Element)}
     */
    public static Object value(Element elem, Object def) {
        return ofNullable(elem).map(Queries::value).orElse(def);
    }

    /**
     * If the {@link Element} passed in is a {@link JsonObject} then
     * return its {@link JsonObject#members() members}, otherwise an empty {@link Map}.
     *
     * @param elem an element, may be {@code null}.
     * @return the members of a JsonObject, or an empty map
     */
    public static Map<String, Element> members(Element elem) {
        return switch(elem) {
            case JsonObject jo -> jo.members();
            case null, default -> Map.of();
        };
    }

    /**
     * Shortcut to {@link #members(Element)} followed by
     * {@link Map#get(Object)} with the non-null member name.
     *
     * @param elem an arbitrary JSON element, maybe {@code null}
     * @param name a member name, may not be {@code null}
     * @return the member of the element given it is a {@link JsonObject}
     */
    public static Element get(Element elem, String name) {
        return members(elem).get(requireNonNull(name));
    }

    /**
     * If the {@link Element} passed in is a {@link JsonArray} then
     * return its {@link JsonArray#elements() elements}, otherwise an empty {@link List}.
     *
     * @param elem an element, may be {@code null}
     * @return the elements of a JsonArray, or an empty list
     */
    public static List<Element> elements(Element elem) {
        return switch (elem) {
            case JsonArray ja -> ja.elements();
            case null, default -> List.of();
        };
    }

    /**
     * Converts an {@link Element element} to {@code int}.
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
    public static int intValue(Element elem, int def) {
        return switch (elem) {
            case null -> def;
            case JsonNumber n -> (int) n.numVal();
            case TRUE -> 1;
            case FALSE -> 0;
            case JsonString s -> Integer.parseInt(s.value());
            case JsonNull ignored -> 0;
            case Aggregate a -> throw new IllegalArgumentException("cannot convert to int: " + a);
        };
    }

    public static int intValue(Element elem) {
        return intValue(requireNonNull(elem), 0);
    }

    public static long longValue(Element elem, long def) {
        return switch (elem) {
            case null -> def;
            case JsonNumber n -> (long) n.numVal();
            case TRUE -> 1L;
            case FALSE -> 0L;
            case JsonString s -> Long.parseLong(s.value());
            case JsonNull ignored -> 0L;
            case Aggregate a -> throw new IllegalArgumentException("cannot convert to long: " + a);
        };
    }

    public static long longValue(Element elem) {
        return longValue(requireNonNull(elem), 0);
    }

    public static double doubleValue(Element elem, double def) {
        return switch (elem) {
            case null -> def;
            case JsonNumber n -> n.numVal();
            case TRUE -> 1d;
            case FALSE -> 0d;
            case JsonString s -> Double.parseDouble(s.value());
            case JsonNull ignored -> 0d;
            case Aggregate a -> throw new IllegalArgumentException("cannot convert to double: " + a);
        };
    }

    public static double doubleValue(Element elem) {
        return doubleValue(requireNonNull(elem), 0d);
    }

    public static <E extends Enum<E>> E enumValue(Class<E> enumClass, Element elem, E def) {
        return switch (elem) {
            case null -> def;
            case JsonString js -> Enum.valueOf(enumClass, js.value());
            default -> throw new IllegalArgumentException("cannot convert to enum: " + elem);
        };
    }

    public static <E extends Enum<E>> E enumValue(Class<E> enumClass, Element elem) {
        return enumValue(enumClass, elem, (E) null);
    }

    public static <E extends Enum<E>> E enumValueIgnoreCase(Class<E> enumClass, Element elem) {
        if (elem instanceof JsonString js) {
            return stream(enumClass.getEnumConstants())
                    .collect(toMap(c -> c.name().toUpperCase(), identity()))
                    .get(js.value().toUpperCase());
        } else {
            throw new IllegalArgumentException("cannot convert to enum: " + elem);
        }
    }

    public static <E extends Enum<E>> E enumValue(Class<E> enumClass, Element
            elem, Function<Element, String> extractor) {
        return extractor
                .andThen(s -> Enum.valueOf(enumClass, s))
                .apply(elem);
    }

    public static String stringValue(Element elem, String def) {
        return switch (elem) {
            case null -> def;
            case JsonString s -> s.value();
            case JsonNull ignored -> "null";
            case JsonNumber n -> Double.toString(n.numVal());
            case JsonBoolean b -> Boolean.toString(b == TRUE);
            case JsonArray a -> a.elements().toString();
            case JsonObject o -> o.members().toString();
        };
    }

    public static String stringValue(Element elem) {
        return stringValue(requireNonNull(elem), null);
    }

    public static boolean booleanValue(Element elem, boolean def) {
        return switch (elem) {
            case null -> def;
            case JsonBoolean b -> b == TRUE;
            case JsonString js -> Boolean.parseBoolean(js.value());
            case JsonNumber jn -> Double.compare(0, jn.numVal()) != 0;
            default -> throw new IllegalArgumentException("cannot convert to boolean: " + elem);
        };
    }

    public static boolean booleanValue(Element elem) {
        return booleanValue(requireNonNull(elem), false);
    }

    private static int[] intArray(JsonArray ja) {
        var tmp = new int[ja.size()];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = intValue(ja.elements().get(i));
        }
        return tmp;
    }

    public static int[] intArray(Element elem) {
        return switch(elem) {
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

    public static long[] longArray(Element elem) {
        return switch(elem) {
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

    public static char[] charArray(Element elem) {
        return switch(elem) {
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

    public static short[] shortArray(Element elem) {
        return switch(elem) {
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

    public static byte[] byteArray(Element elem) {
        return switch(elem) {
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

    public static double[] doubleArray(Element elem) {
        return switch(elem) {
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

    public static float[] floatArray(Element elem) {
        return switch(elem) {
            case JsonArray ja -> floatArray(ja);
            case null, default -> new float[0];
        };
    }


    private static boolean[] booleanArray(JsonArray ja) {
        var tmp = new boolean[ja.size()];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = booleanValue(ja.elements().get(i));
        }
        return tmp;
    }

    public static boolean[] booleanArray(Element elem) {
        return switch(elem) {
            case JsonArray ja -> booleanArray(ja);
            case null, default -> new boolean[0];
        };
    }

}
