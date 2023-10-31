package com.github.ralfspoeth.json;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class AggregateFunctionsTest {
    @Test
    void testObjectAsStringFunction() {
        var src = Element.objectBuilder()
                .named("one", JsonBoolean.TRUE)
                .named("two", JsonNull.INSTANCE)
                .named("five", new JsonNumber(5))
                .build();
        var lst = Stream.of("one", "two", "five").map(src).toList();
        Assertions.assertEquals(List.of(JsonBoolean.TRUE, JsonNull.INSTANCE, new JsonNumber(5)), lst);
    }

    @Test
    void testArrayAsIntFunction() {
        var src = Element.arrayBuilder()
                .item(JsonNull.INSTANCE)
                .item(JsonBoolean.FALSE)
                .item(new JsonString("3"))
                .build();
        var lst = IntStream.range(0, 3).mapToObj(src).toList();
        Assertions.assertEquals(List.of(JsonNull.INSTANCE, JsonBoolean.FALSE, new JsonString("3")), lst);
    }

}
