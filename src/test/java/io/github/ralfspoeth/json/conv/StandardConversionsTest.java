package io.github.ralfspoeth.json.conv;

import io.github.ralfspoeth.json.*;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static io.github.ralfspoeth.json.conv.StandardConversions.*;
import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void testStringValue() {
        assertAll(
                () -> assertEquals("one", stringValue(new JsonString("one")))
        );
    }

    @Test
    void testBooleanValue() {
        assertAll(
                () -> assertTrue(booleanValue(JsonBoolean.TRUE)),
                () -> assertFalse(booleanValue(JsonBoolean.FALSE)),
                () -> assertTrue(booleanValue(new JsonString("true"))),
                () -> assertTrue(booleanValue(new JsonString("TrUe"))),
                () -> assertFalse(booleanValue(new JsonString("false"))),
                () -> assertFalse(booleanValue(new JsonString("XXX"))),
                () -> assertThrows(IllegalArgumentException.class, () -> booleanValue(new JsonNumber(1d)))
        );
    }


    @Test
    void testEnumValue() {
        enum E {ONE, TWO}
        var obj = Aggregate.objectBuilder().named("e", new JsonString("onet")).build();
        Function<Element, String> extr = elem -> switch (elem){
            case JsonObject jo -> stringValue(jo.members().get("e")).substring(0, 3).toUpperCase();
            default -> throw new IllegalArgumentException("failed");
        };
        assertAll(
                () -> assertEquals(E.ONE, enumValue(E.class, new JsonString("ONE"))),
                () -> assertNotEquals(E.TWO, enumValue(E.class, new JsonString("ONE"))),
                () -> assertThrows(IllegalArgumentException.class, () -> enumValue(E.class, new JsonString("one"))),
                () -> assertEquals(E.ONE, enumValueIgnoreCase(E.class, new JsonString("one"))),
                () -> assertEquals(E.ONE, enumValue(E.class, obj, extr))
        );
    }


    @Test
    void testAs() {
        record R(int x, int y) {

        }
    }


}
