package io.github.ralfspoeth.json.data;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BasicTest {

    @Test
    void testOfNull() {
        assertEquals(JsonNull.INSTANCE, Basic.of(null));
    }

    @Test
    void testPredicateNature() {
        // given
        var basics = List.of(
                JsonNull.INSTANCE,
                JsonBoolean.TRUE, JsonBoolean.FALSE,
                Basic.of(5),
                new JsonString("hello")
        );
        // when
        // then
        assertAll(
                () -> assertEquals(JsonNull.INSTANCE, basics.stream().filter(JsonNull.INSTANCE).findFirst().orElse(null)),
                () -> assertEquals(Basic.of(5), basics.stream().filter(Basic.of(5)).findFirst().orElse(null)),
                () -> assertEquals(Basic.of("hello"), basics.stream().filter(new JsonString("hello")).findFirst().orElse(null)),
                () -> assertEquals(JsonBoolean.TRUE, basics.stream().filter(JsonBoolean.TRUE).findFirst().orElse(null)),
                () -> assertEquals(JsonBoolean.FALSE, basics.stream().filter(JsonBoolean.FALSE).findFirst().orElse(null))
        );
    }

    @Test
    void testOfBoolean() {
        assertAll(
                () -> assertEquals(JsonBoolean.TRUE, Basic.of(true)),
                () -> assertEquals(JsonBoolean.FALSE, Basic.of(false))
        );
    }

    @Test
    void testOfNums() {
        Basic<@NonNull BigDecimal> five = new JsonNumber(BigDecimal.valueOf(5));

        assertAll(
                () -> assertEquals(five, Basic.of(5)),
                () -> assertEquals(five, Basic.of(5L)),
                () -> assertEquals(five, Basic.of((short)5)),
                () -> assertEquals(five, Basic.of((char)5)),
                () -> assertEquals(five, Basic.of((byte)5)),
                () -> assertEquals(five, Basic.of(5.0f)),
                () -> assertEquals(five, Basic.of(5.0)),
                () -> assertEquals(five, Basic.of(BigDecimal.valueOf(5))),
                () -> assertEquals(five, Basic.of(BigInteger.valueOf(5)))
        );
    }
}
