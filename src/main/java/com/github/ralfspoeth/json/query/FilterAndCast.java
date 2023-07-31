package com.github.ralfspoeth.json.query;

import com.github.ralfspoeth.json.*;

import java.util.stream.Stream;

public class FilterAndCast {
    private FilterAndCast() {
    }

    public static Stream<JsonObject> objects(Stream<Element> src) {
        return src.filter(e -> e instanceof JsonObject).map(JsonObject.class::cast);
    }

    public static Stream<JsonArray> arrays(Stream<Element> src) {
        return src.filter(e -> e instanceof JsonArray).map(JsonArray.class::cast);
    }

    public static Stream<JsonNumber> numbers(Stream<Element> src) {
        return src.filter(e -> e instanceof JsonNumber).map(JsonNumber.class::cast);
    }

    public static Stream<JsonString> strings(Stream<Element> src) {
        return src.filter(e -> e instanceof JsonString).map(JsonString.class::cast);
    }

    public static Stream<JsonBoolean> booleans(Stream<Element> src) {
        return src.filter(e -> e instanceof JsonBoolean).map(JsonBoolean.class::cast);
    }

    public static Stream<Basic> values(Stream<Element> src) {
        return src.filter(e -> e instanceof Basic).map(Basic.class::cast);
    }

    public static Stream<Aggregate> aggregates(Stream<Element> src) {
        return src.filter(e -> e instanceof Aggregate).map(Aggregate.class::cast);
    }
}
