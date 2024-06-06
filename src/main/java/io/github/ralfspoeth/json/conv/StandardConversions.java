package io.github.ralfspoeth.json.conv;

import io.github.ralfspoeth.json.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.github.ralfspoeth.basix.fn.Functions.indexed;
import static io.github.ralfspoeth.basix.fn.Predicates.eq;
import static io.github.ralfspoeth.json.JsonBoolean.FALSE;
import static io.github.ralfspoeth.json.JsonBoolean.TRUE;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;


public class StandardConversions {

    private StandardConversions() {
        // prevent instantiation
    }

    private static Map<String, ?> asMap(Element elem) {
        return switch (elem) {
            case JsonObject jo -> jo.members()
                    .entrySet()
                    .stream()
                    .filter(not(eq(JsonNull.INSTANCE, Map.Entry::getValue)))
                    .collect(toMap(Map.Entry::getKey, e -> asObject(e.getValue())));
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
                    .map(StandardConversions::asObject)
                    .toList();
            case null, default -> throw new IllegalArgumentException(elem + " is not a JSON array");
        };
    }

    /**
     * Provides the most natural mapping of a JSON element
     * to their Java counterparts.
     *
     * A {@link JsonObject object} is basically converted into its map of
     * {@link JsonObject#members() members},
     * the values of which are passed to this method recursively.
     * An {@link JsonArray array} is represented by a {@link List}
     * with this function applied to all
     * its {@link JsonArray#elements() elements}.
     * All other {@link Basic} elements are converted using the basic's
     * {@link Basic#value()} function.
     *
     * @param elem a JSON element
     * @return either a {@link Map}, a {@link List}
     * or a {@code String}, {@code double}, {@code null}, or {@code boolean}
     */
    public static Object asObject(Element elem) {
        return switch (elem) {
            case JsonObject jo -> asMap(jo);
            case JsonArray ja -> asList(ja);
            case Basic<?> basic -> asBasic(basic);
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

    private static Object primitiveValue(Class<?> type, Element elem) {
        if (type.equals(int.class)) {
            return intValue(elem, 0);
        } else if (type.equals(long.class)) {
            return longValue(elem, 0l);
        } else if (type.equals(double.class)) {
            return doubleValue(elem, 0d);
        } else if (type.equals(float.class)) {
            return (float) doubleValue(elem, 0d);
        } else if (type.equals(short.class)) {
            return (short) intValue(elem, 0);
        } else if (type.equals(char.class)) {
            return (char) intValue(elem, 0);
        } else if (type.equals(byte.class)) {
            return (byte) intValue(elem, 0);
        } else if (type.equals(boolean.class)) {
            return booleanValue(elem);
        } else throw new AssertionError();
    }


    private static <T> T instanceFromString(Class<T> clazz, String string) {
        var parameterTypes = new Class[]{String.class};

        return stream(clazz.getDeclaredConstructors())
                .filter(c -> Arrays.equals(c.getParameterTypes(), parameterTypes))
                .map(c -> newInst(c, string))
                .map(clazz::cast)
                .findFirst().or(() ->
                        stream(clazz.getDeclaredMethods())
                                .filter(m -> Modifier.isStatic(m.getModifiers()))
                                .filter(m -> Arrays.equals(m.getParameterTypes(), parameterTypes))
                                .filter(m -> m.getReturnType().equals(clazz))
                                .map(m -> invoke(m, clazz, string))
                                .findFirst()
                ).orElseThrow();
    }

    private static <T> T newInst(Constructor<T> constructor, Object... args) {
        try {
            return constructor.newInstance(args);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> T invoke(Method m, Class<T> clazz, Object... args) {
        try {
            return (T) m.invoke(clazz, args);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
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
            default -> throw new IllegalArgumentException("cannot convert to boolean: " + elem);
        };
    }

    public static boolean booleanValue(Element elem) {
        return booleanValue(requireNonNull(elem), false);
    }


    public static <T> T as(Class<T> targetType, Element element) {
        if (Number.class.isAssignableFrom(targetType)) {
            return (T) asNumber((Class<Number>) targetType, element);
        } else if (targetType.isRecord() && element instanceof JsonObject jo) {
            return (T) asRecord((Class<Record>) targetType, jo);
        } else if (targetType.isArray() && element instanceof JsonArray) {
            var t = targetType.getComponentType();
            return (T) asArray(t, element);
        } else if (Collection.class.isAssignableFrom(targetType) && element instanceof JsonArray) {
            return (T) asCollection(targetType, element);
        } else if(element instanceof JsonString js) {
            return as(targetType, js.value());
        } else if(element == null) {
            return null;
        }
        else {
            throw new IllegalArgumentException("%s cannot be converted into %s".formatted(element, targetType));
        }
    }

    private static <R extends Record> R asRecord(Class<R> type, JsonObject e) {
        assert type.isRecord();
        var comps = type.getRecordComponents();
        var compTypes = new Class<?>[comps.length];
        var vals = new Object[comps.length];
        stream(comps)
                .map(indexed(0))
                .forEach(ic -> {
                    var ctype = ic.value().getType();
                    var member = e.members().get(ic.value().getName());
                    compTypes[ic.index()] = ctype;
                    if (ctype.isPrimitive()) {
                        vals[ic.index()] = primitiveValue(ctype, member);
                    }
                    // well known classes
                    else if (ctype.equals(String.class)) {
                        vals[ic.index()] = stringValue(member);
                    } else if (ctype.equals(BigDecimal.class)) {
                        vals[ic.index()] = new BigDecimal(stringValue(member));
                    } else if (ctype.equals(BigInteger.class)) {
                        vals[ic.index()] = new BigInteger(stringValue(member));
                    }
                    // generic class case
                    else {
                        vals[ic.index()] = as(ic.value().getType(), e.members().get(ic.value().getName()));
                    }
                });
        try {
            var constructor = type.getDeclaredConstructor(compTypes);
            return constructor.newInstance(vals);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                 IllegalAccessException ex) {
            throw new RuntimeException(ex);
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

    private static Collection<?> asCollection(Class<?> collClass, Element element) {
        try {
            var cons = collClass.getDeclaredConstructor(Collection.class);
            var array = (Object[]) asArray(Object.class, element);
            return (Collection<?>) cons.newInstance(Arrays.asList(array));
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        }
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
            case JsonNull ignored -> asNumType(numType, 0d);
            case null -> asNumType(numType, 0d);
            default -> throw new IllegalArgumentException(el + " cannot be cast into " + numType);
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

    private static final Map<MethodType, MethodHandle> bestHandles = new HashMap<>();

    private static <T> T as(Class<T> type, String text) {
        var mt = MethodType.methodType(type, String.class);
        return invokeHandle(
                bestHandles.computeIfAbsent(mt, StandardConversions::findBestHandle),
                text
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokeHandle(MethodHandle handle, String text) {
        try {
            return (T)handle.invoke(text);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static MethodHandle findBestHandle(MethodType mt) {
        var type = mt.returnType();
        var lu = MethodHandles.publicLookup();

        return Stream.of(type.getDeclaredMethods())
                .filter(m -> Modifier.isStatic(m.getModifiers())) // only static methods
                .filter(m -> type.isAssignableFrom(m.getReturnType())) // proper return type
                .filter(m -> m.getParameterCount() == mt.parameterCount())
                .filter(m -> Arrays.stream(m.getParameterTypes()).map(indexed(0))
                        .allMatch(i -> i.value().isAssignableFrom(mt.parameterType(i.index()))))// same params
                .map(m -> toStaticHandle(m.getName(), MethodType.methodType(mt.returnType(), m.getParameterTypes())))
                .findFirst()
                .or(() -> Optional.of(toConstructorHandle(mt, lu, type)))
                .orElseThrow(() -> new IllegalArgumentException("No static factory or constructor for " + mt));
    }

    private static MethodHandle toConstructorHandle(MethodType mt, MethodHandles.Lookup lu, Class<?> type) {
        try {
            return lu.findConstructor(type, mt);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static MethodHandle toStaticHandle(String name, MethodType type) {
        var lu = MethodHandles.publicLookup();
        try {
            return lu.findStatic(type.returnType(), name, type);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
