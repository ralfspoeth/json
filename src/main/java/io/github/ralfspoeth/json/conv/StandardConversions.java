package io.github.ralfspoeth.json.conv;

import io.github.ralfspoeth.json.*;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.stream.Collectors;

public class StandardConversions {

    private StandardConversions(){
        // prevent instantiation
    }

    public static int intValue(Element elem) {
        return switch (elem) {
            case JsonNumber n -> (int) n.numVal();
            case JsonBoolean b -> switch (b) {
                case TRUE -> 1;
                case FALSE -> 0;
            };
            case JsonString s -> Integer.parseInt(s.value());
            case JsonNull ignored -> 0;
            case Aggregate a -> throw new IllegalArgumentException("cannot convert to int: " + a);
        };
    }

    public static long longValue(Element elem) {
        return switch (elem) {
            case JsonNumber n -> (long) n.numVal();
            case JsonBoolean b -> switch (b) {
                case TRUE -> 1L;
                case FALSE -> 0L;
            };
            case JsonString s -> Long.parseLong(s.value());
            case JsonNull ignored -> 0L;
            case Aggregate a -> throw new IllegalArgumentException("cannot convert to long: " + a);
        };
    }

    public static double doubleValue(Element elem) {
        return switch (elem) {
            case JsonNumber n -> n.numVal();
            case JsonBoolean b -> switch (b) {
                case TRUE -> 1d;
                case FALSE -> 0d;
            };
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

    public static String stringValue(Element elem) {
        return switch (elem) {
            case JsonString s -> s.value();
            case JsonNull ignored -> "null";
            case JsonNumber n -> Double.toString(n.numVal());
            case JsonBoolean b -> Boolean.toString(b == JsonBoolean.TRUE);
            case JsonArray a -> a.elements().toString();
            case JsonObject o -> o.members().toString();
        };
    }

    public static boolean booleanValue(Element elem) {
        return switch (elem) {
            case JsonBoolean b -> b == JsonBoolean.TRUE;
            case JsonString js -> Boolean.parseBoolean(js.value());
            default -> throw new IllegalArgumentException("cannot convert to boolean: " + elem);
        };
    }

    private static <R extends Record> R asRecord(Class<R> type, JsonObject e) {
        assert type.isRecord();
        var comps = type.getRecordComponents();
        var compTypes = new Class<?>[comps.length];
        var vals = new Object[comps.length];
        for(int i=0; i<comps.length; i++) {
            compTypes[i] = comps[i].getType();
            vals[i] = as(compTypes[i], e.members().get(comps[i].getName()));
        }
        try {
            var constructor = type.getDeclaredConstructor(compTypes);
            return constructor.newInstance(vals);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static <T> T as(Class<T> compType, Element element) {
        if(compType.isRecord() && element instanceof JsonObject jo) {
            return (T) asRecord((Class<Record>)compType, jo);
        } else if(compType.isArray() && element instanceof JsonArray) {
            var t = compType.getComponentType();
            return (T)asArray(t, element);
        }
        else {
            throw new IllegalArgumentException();
        }
    }

    private static Object asArray(Class<?> type, Element element) {
        return switch (element) {
            case JsonArray ja -> {
                var array = java.lang.reflect.Array.newInstance(type, ja.size());
                for(int i=0; i<ja.size(); i++) {
                    Array.set(array, i, as(type, ja.elements().get(i)));
                }
                yield array;
            }
            default -> throw new AssertionError();

        };
    }
}
