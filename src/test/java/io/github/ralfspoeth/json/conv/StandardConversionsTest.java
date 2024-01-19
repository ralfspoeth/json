package io.github.ralfspoeth.json.conv;

import io.github.ralfspoeth.json.JsonBoolean;
import io.github.ralfspoeth.json.JsonNumber;
import io.github.ralfspoeth.json.JsonString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.github.ralfspoeth.json.conv.StandardConversions.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StandardConversionsTest {

    @Test
    void testIntValue() {
        assertAll(
                () -> assertEquals(1, intValue(new JsonNumber(1))),
                () -> assertEquals(1, intValue(new JsonNumber(1.1d))),
                () -> assertEquals(1, intValue(new JsonString("1"))),
                () -> assertEquals(1, intValue(JsonBoolean.TRUE)),
                () -> assertEquals(0, intValue(JsonBoolean.FALSE))
        );
    }

    @Test
    void testLongValue() {
        assertAll(
                () -> assertEquals(1L, longValue(new JsonNumber(1))),
                () -> assertEquals(1L, longValue(new JsonNumber(1.1d))),
                () -> assertEquals(1L, longValue(new JsonString("1"))),
                () -> assertEquals(1L, longValue(JsonBoolean.TRUE)),
                () -> assertEquals(0L, longValue(JsonBoolean.FALSE))
        );
    }

    @Test
    void testDoubleValue() {
        assertAll(
                () -> assertEquals(1d, doubleValue(new JsonNumber(1))),
                () -> assertEquals(1.1d, doubleValue(new JsonNumber(1.1d))),
                () -> assertEquals(1d, doubleValue(new JsonString("1"))),
                () -> assertEquals(1.5d, doubleValue(new JsonString("1.5"))),
                () -> assertEquals(1d, doubleValue(JsonBoolean.TRUE)),
                () -> assertEquals(0d, doubleValue(JsonBoolean.FALSE))
        );
    }



}
