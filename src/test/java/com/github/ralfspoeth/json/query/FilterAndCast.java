package com.github.ralfspoeth.json.query;

import com.github.ralfspoeth.json.*;

import java.util.stream.Stream;

public class FilterAndCast {
    private FilterAndCast() {
    }

    public static Stream<JsonObject> objectStream(Stream<JsonElement> src) {
        return src.filter(e -> e instanceof JsonObject).map(JsonObject.class::cast);
    }

    public static Stream<JsonArray> arrayStream(Stream<JsonElement> src) {
        return src.filter(e -> e instanceof JsonArray).map(JsonArray.class::cast);
    }

    public static Stream<JsonNumber> numberStream(Stream<JsonElement> src) {
        return src.filter(e -> e instanceof JsonNumber).map(JsonNumber.class::cast);
    }

    public static Stream<JsonString> stringStream(Stream<JsonElement> src) {
        return src.filter(e -> e instanceof JsonString).map(JsonString.class::cast);
    }

    public static Stream<JsonBoolean> booleanStream(Stream<JsonElement> src) {
        return src.filter(e -> e instanceof JsonBoolean).map(JsonBoolean.class::cast);
    }

    public static Stream<JsonValue> valueStream(Stream<JsonElement> src) {
        return src.filter(e -> e instanceof JsonValue).map(JsonValue.class::cast);
    }

    public static Stream<JsonAggregate> aggregateStream(Stream<JsonElement> src) {
        return src.filter(e -> e instanceof JsonAggregate).map(JsonAggregate.class::cast);
    }
}
