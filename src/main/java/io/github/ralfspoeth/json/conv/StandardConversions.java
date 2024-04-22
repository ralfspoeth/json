package io.github.ralfspoeth.json.conv;

import io.github.ralfspoeth.json.*;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.JsonBoolean.FALSE;
import static io.github.ralfspoeth.json.JsonBoolean.TRUE;

public class StandardConversions {

    private StandardConversions() {
        // prevent instantiation
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
    public static int intValue(Element elem) {
        return switch (elem) {
            case JsonNumber n -> (int) n.numVal();
            case TRUE -> 1;
            case FALSE -> 0;
            case JsonString s -> Integer.parseInt(s.value());
            case JsonNull ignored -> 0;
            case Aggregate a -> throw new IllegalArgumentException("cannot convert to int: " + a);
        };
    }

    public static long longValue(Element elem) {
        return switch (elem) {
            case JsonNumber n -> (long) n.numVal();
            case TRUE -> 1L;
            case FALSE -> 0L;
            case JsonString s -> Long.parseLong(s.value());
            case JsonNull ignored -> 0L;
            case Aggregate a -> throw new IllegalArgumentException("cannot convert to long: " + a);
        };
    }

    public static double doubleValue(Element elem) {
        return switch (elem) {
            case JsonNumber n -> n.numVal();
            case TRUE -> 1d;
            case FALSE -> 0d;
            case JsonString s -> Double.parseDouble(s.value());
            case JsonNull ignored -> 0d;
            case Aggregate a -> throw new IllegalArgumentException("cannot convert to double: " + a);
        };
    }

    public static <E extends Enum<E>> E enumValue(Class<E> enumClass, Element elem) {
        if (elem instanceof JsonString js) {
            return Enum.valueOf(enumClass, js.value());
        } else {
            throw new IllegalArgumentException("cannot convert to enum: " + elem);
        }
    }

    public static <E extends Enum<E>> E enumValueIgnoreCase(Class<E> enumClass, Element elem) {
        if (elem instanceof JsonString js) {
            return Arrays.stream(enumClass.getEnumConstants())
                    .collect(Collectors.toMap(c -> c.name().toUpperCase(), c -> c))
                    .get(js.value().toUpperCase());
        } else {
            throw new IllegalArgumentException("cannot convert to enum: " + elem);
        }
    }

    public static <E extends Enum<E>> E enumValue(Class<E> enumClass, Element elem, Function<Element, String> extractor) {
        return Enum.valueOf(enumClass, extractor.apply(elem));
    }

    public static String stringValue(Element elem) {
        return switch (elem) {
            case JsonString s -> s.value();
            case JsonNull ignored -> "null";
            case JsonNumber n -> Double.toString(n.numVal());
            case JsonBoolean b -> Boolean.toString(b == TRUE);
            case JsonArray a -> a.elements().toString();
            case JsonObject o -> o.members().toString();
        };
    }

    public static boolean booleanValue(Element elem) {
        return switch (elem) {
            case JsonBoolean b -> b == TRUE;
            case JsonString js -> Boolean.parseBoolean(js.value());
            default -> throw new IllegalArgumentException("cannot convert to boolean: " + elem);
        };
    }

    private static <R extends Record> R asRecord(Class<R> type, JsonObject e) {
        assert type.isRecord();
        var comps = type.getRecordComponents();
        var compTypes = new Class<?>[comps.length];
        var vals = new Object[comps.length];
        for (int i = 0; i < comps.length; i++) {
            compTypes[i] = comps[i].getType();
            vals[i] = as(compTypes[i], e.members().get(comps[i].getName()));
        }
        try {
            var constructor = type.getDeclaredConstructor(compTypes);
            return constructor.newInstance(vals);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                 IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static <T> T as(Class<T> compType, Element element) {
        if (compType.isRecord() && element instanceof JsonObject jo) {
            return (T) asRecord((Class<Record>) compType, jo);
        } else if (compType.isArray() && element instanceof JsonArray) {
            var t = compType.getComponentType();
            return (T) asArray(t, element);
        } else {
            throw new IllegalArgumentException();
        }
    }

    private static Object asArray(Class<?> type, Element element) {
        if (element instanceof JsonArray ja) {
            var array = java.lang.reflect.Array.newInstance(type, ja.size());
            for (int i = 0; i < ja.size(); i++) {
                Array.set(array, i, as(type, ja.elements().get(i)));
            }
            return array;
        } else throw new AssertionError();
    }

    private static <T extends Number> T asNumber(Class<T> numType, Element el) {
        return switch (el) {
            case JsonNumber n -> {
                Double doubleValue = n.value();
                yield asNumType(numType, doubleValue);
            }
            case JsonString s -> {
                var doubleValue = Double.parseDouble(s.value());
                yield asNumType(numType, doubleValue);
            }
            case TRUE -> asNumType(numType, 1d);
            case FALSE -> asNumType(numType, 0d);
            case JsonNull n -> asNumType(numType, 0d);
            case null, default -> throw new IllegalArgumentException(el + " cannot be cast into " + numType);
        };
    }

    private static <T extends Number> T asNumType(Class<T> numType, Double val) {
        if (numType.equals(Double.class)) {
            return (T) val;
        } else if (numType.equals(Float.class)) {
            return (T) Float.valueOf(val.floatValue());
        } else if (numType.equals(Integer.class)) {
            return (T) Integer.valueOf(val.intValue());
        } else if (numType.equals(Long.class)) {
            return (T) Long.valueOf(val.longValue());
        } else if (numType.equals(Short.class)) {
            return (T) Short.valueOf(val.shortValue());
        } else if (numType.equals(Byte.class)) {
            return (T) Byte.valueOf(val.byteValue());
        } else {
            throw new IllegalArgumentException(val + " cannot be cast into " + numType);
        }
    }

    private static <T> T as(Class<T> type, String text) {
        // find factory method
        return Stream.of(type.getDeclaredMethods())
                .filter(m -> Modifier.isStatic(m.getModifiers()))
                .filter(m -> type.isAssignableFrom(m.getReturnType()))
                .filter(m -> m.getParameterCount() == 1)
                .filter(m -> m.getParameterTypes()[0].equals(String.class))
                .map(m -> invokeStaticMethod(m, text))
                .map(type::cast)
                .findFirst()
                .or(() -> Stream.of(type.getDeclaredConstructors())
                        .filter(c -> c.getParameterCount() == 1)
                        .filter(c -> c.getParameterTypes()[0].equals(String.class))
                        .map(c -> newInstance(c, text))
                        .map(type::cast)
                        .findFirst()
                )
                .orElseThrow(() -> new IllegalArgumentException("Cannot convert " + text + " into " + type));
    }

    private static Object invokeStaticMethod(Method m, String arg) {
        try {
            return m.invoke(null, arg);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static <T> T newInstance(Constructor<T> constructor, String text) {
        try {
            return constructor.newInstance(text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Extract the element of a single-valued {@link Aggregate} or the
     * {@link Basic} element passed in; throws {@link IllegalArgumentException}
     * when {@code null} or empty or multivalued {@link Aggregate} instance.
     * <p>
     * If the argument is {@link JsonObject} then the {@link Map.Entry#getValue()}  value}
     * of the first member is returned.
     *
     * @param elem an element; may not be null
     * @return the one and only single
     */
    public static Element single(Element elem) {
        return switch (elem) {
            case JsonObject o when o.size() == 1 -> o.members().values().stream()
                    .findFirst()
                    .orElseThrow(() -> new AssertionError());
            case JsonArray a when a.size() == 1 -> a.elements().getFirst();
            case Aggregate ignored -> throw new IllegalArgumentException(
                    "cannot extract the one and only element from " + elem
            );
            case Basic b -> b;
            case null -> throw new IllegalArgumentException("cannot extract single from null");
        };
    }
}
