package com.github.ralfspoeth.json.query;

import com.github.ralfspoeth.json.*;

import java.util.function.Function;
import java.util.stream.Stream;

public class FilterAndCast {
    private FilterAndCast() {
    }

    public static <T> Function<Object, Stream<T>> filterAndCast(Class<T> c) {
        return obj -> c.isInstance(obj)
                ? Stream.of(obj).map(c::cast)
                : Stream.empty();
    }

    public static Stream<JsonObject> objects(Stream<Element> src) {
        return src.flatMap(filterAndCast(JsonObject.class));
    }

    public static Stream<JsonArray> arrays(Stream<Element> src) {
        return src.flatMap(filterAndCast(JsonArray.class));
    }

    public static Stream<JsonNumber> numbers(Stream<Element> src) {
        return src.flatMap(filterAndCast(JsonNumber.class));
    }

    public static Stream<JsonString> strings(Stream<Element> src) {
        return src.flatMap(filterAndCast(JsonString.class));
    }

    public static Stream<JsonBoolean> booleans(Stream<Element> src) {
        return src.flatMap(filterAndCast(JsonBoolean.class));
    }

    public static Stream<Basic> values(Stream<Element> src) {
        return src.flatMap(filterAndCast(Basic.class));
    }

    public static Stream<Aggregate> aggregates(Stream<Element> src) {
        return src.flatMap(filterAndCast(Aggregate.class));
    }
}
