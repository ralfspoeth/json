package io.github.ralfspoeth.json.data;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class BasicTest {

    @Test
    void testValue() {
        assertAll(
                () -> assertNull(JsonNull.INSTANCE.value()),
                () -> assertTrue(JsonBoolean.TRUE.value()),
                () -> assertFalse(JsonBoolean.FALSE.value()),
                () -> assertEquals("hello", new JsonString("hello").value()),
                () -> assertEquals(BigDecimal.TWO, new JsonNumber(BigDecimal.valueOf(2)).value())
        );
    }

    @Test
    void testOfNull() {
        assertEquals(JsonNull.INSTANCE, Basic.of(null));
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
