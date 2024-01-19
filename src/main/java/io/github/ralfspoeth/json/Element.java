package io.github.ralfspoeth.json;

public sealed interface Element permits Aggregate, Basic {

    default int intValue() {
        return switch (this) {
            case JsonNumber n -> (int) n.numVal();
            case JsonBoolean b -> switch (b) {
                case TRUE -> 1;
                case FALSE -> 0;
            };
            case JsonString s -> Integer.parseInt(s.value());
            case JsonNull ignored -> 0;
            case Aggregate a -> throw new IllegalStateException("cannot convert to int: " + a);
        };
    }

    default long longValue() {
        return switch (this) {
            case JsonNumber n -> (long) n.numVal();
            case JsonBoolean b -> switch (b) {
                case TRUE -> 1L;
                case FALSE -> 0L;
            };
            case JsonString s -> Long.parseLong(s.value());
            case JsonNull ignored -> 0L;
            case Aggregate a -> throw new IllegalStateException("cannot convert to long: " + a);
        };
    }

    default double doubleValue() {
        return switch (this) {
            case JsonNumber n -> n.numVal();
            case JsonBoolean b -> switch (b) {
                case TRUE -> 1d;
                case FALSE -> 0d;
            };
            case JsonString s -> Double.parseDouble(s.value());
            case JsonNull ignored -> 0d;
            case Aggregate a -> throw new IllegalStateException("cannot convert to double: " + a);
        };
    }

    default <E extends Enum<E>> E enumValue(Class<E> enumClass) {
        throw new IllegalStateException("cannot convert to enum: " + this);
    }

    default <E extends Enum<E>> E enumValueIgnoreCase(Class<E> enumClass) {
        throw new IllegalStateException("cannot convert to enum: " + this);
    }

    default String stringValue() {
        return switch (this) {
            case JsonString s -> s.value();
            case JsonNull ignored -> "null";
            case JsonNumber n -> Double.toString(n.numVal());
            case JsonBoolean b -> Boolean.toString(b == JsonBoolean.TRUE);
            case JsonArray a -> a.elements().toString();
            case JsonObject o -> o.members().toString();
        };
    }

    default boolean booleanValue() {
        throw new IllegalStateException("cannot convert to enum: " + this);
    }

    static Element of(Object o) {
        if (o instanceof Record || o instanceof Iterable<?> || o != null && o.getClass().isArray()) {
            return Aggregate.of(o);
        } else {
            return Basic.of(o);
        }
    }
}
