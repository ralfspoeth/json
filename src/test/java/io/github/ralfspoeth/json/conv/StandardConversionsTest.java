package io.github.ralfspoeth.json.conv;

import io.github.ralfspoeth.json.*;
import io.github.ralfspoeth.json.io.JsonReader;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static io.github.ralfspoeth.json.Aggregate.arrayBuilder;
import static io.github.ralfspoeth.json.Aggregate.objectBuilder;
import static io.github.ralfspoeth.json.conv.StandardConversions.*;
import static org.junit.jupiter.api.Assertions.*;

class StandardConversionsTest {

    @Test
    void testIntValue() {
        assertAll(
                () -> assertEquals(1, intValue(new JsonNumber(1), 0)),
                () -> assertEquals(1, intValue(new JsonNumber(1.1d), 0)),
                () -> assertEquals(1, intValue(new JsonString("1"), 0)),
                () -> assertEquals(1, intValue(JsonBoolean.TRUE, 0)),
                () -> assertEquals(0, intValue(JsonBoolean.FALSE, 1)),
                () -> assertThrows(NullPointerException.class, () -> intValue(null))
        );
    }

    @Test
    void testLongValue() {
        assertAll(
                () -> assertEquals(1L, longValue(new JsonNumber(1), 0)),
                () -> assertEquals(1L, longValue(new JsonNumber(1.1d), 0)),
                () -> assertEquals(1L, longValue(new JsonString("1"), 0)),
                () -> assertEquals(1L, longValue(JsonBoolean.TRUE, 0)),
                () -> assertEquals(0L, longValue(JsonBoolean.FALSE, 1)),
                () -> assertThrows(NullPointerException.class, () -> longValue(null))
        );
    }

    @Test
    void testDoubleValue() {
        assertAll(
                () -> assertEquals(1d, doubleValue(new JsonNumber(1), 0)),
                () -> assertEquals(1.1d, doubleValue(new JsonNumber(1.1d), 0)),
                () -> assertEquals(1d, doubleValue(new JsonString("1"), 0)),
                () -> assertEquals(1.5d, doubleValue(new JsonString("1.5"), 0)),
                () -> assertEquals(1d, doubleValue(JsonBoolean.TRUE, 0)),
                () -> assertEquals(0d, doubleValue(JsonBoolean.FALSE, 1)),
                () -> assertThrows(NullPointerException.class, () -> booleanValue(null))
        );
    }

    @Test
    void testStringValue() {
        assertAll(
                () -> assertEquals("one", stringValue(new JsonString("one"), null)),
                () -> assertEquals("1.0", stringValue(new JsonNumber(1d), null)),
                () -> assertEquals("1.1", stringValue(new JsonNumber(1.1d), null)),
                () -> assertEquals("1.123", stringValue(new JsonNumber(1.123d), null)),
                () -> assertEquals("true", stringValue(JsonBoolean.TRUE, null)),
                () -> assertEquals("false", stringValue(JsonBoolean.FALSE, null)),
                () -> assertEquals("null", stringValue(JsonNull.INSTANCE, null)),
                () -> assertThrows(NullPointerException.class, () -> stringValue(null))
        );
    }

    @Test
    void testBooleanValue() {
        assertAll(
                () -> assertTrue(booleanValue(JsonBoolean.TRUE, false)),
                () -> assertFalse(booleanValue(JsonBoolean.FALSE, true)),
                () -> assertTrue(booleanValue(new JsonString("true"), false)),
                () -> assertTrue(booleanValue(new JsonString("TrUe"), false)),
                () -> assertFalse(booleanValue(new JsonString("false"), true)),
                () -> assertFalse(booleanValue(new JsonString("XXX"), true)),
                () -> assertThrows(NullPointerException.class, () -> booleanValue(null))
        );
    }


    @Test
    void testEnumValue() {
        enum E {ONE, TWO}
        var obj = objectBuilder().named("e", new JsonString("onet")).build();
        Function<Element, String> extr = elem -> switch (elem) {
            case JsonObject jo -> stringValue(jo.members().get("e"), null)
                    .substring(0, 3)
                    .toUpperCase();
            default -> throw new IllegalArgumentException("failed");
        }; // extracts the member named "e", first three letters of the value, then to upper case

        assertAll(
                () -> assertEquals(E.ONE, enumValue(E.class, new JsonString("ONE"))),
                () -> assertNotEquals(E.TWO, enumValue(E.class, new JsonString("ONE"))),
                () -> assertEquals(E.TWO, enumValue(E.class, new JsonString("TWO"))),
                () -> assertThrows(IllegalArgumentException.class, () -> enumValue(E.class, new JsonString("one"))),
                () -> assertEquals(E.ONE, enumValueIgnoreCase(E.class, new JsonString("one"))),
                () -> assertEquals(E.ONE, enumValue(E.class, obj, extr))
        );
    }

    @Test
    void testAsJsonArray() {
        boolean[] trueFalse = {true, false};
        int[] one23 = {1, 2, 3};
        double[] four5 = {4.d, 5.d};
        String[] sixes = {"six", "six"};
        Object[] nulls = {null};
        assertAll(
                () -> assertEquals(arrayBuilder().item(JsonBoolean.TRUE).item(JsonBoolean.FALSE).build(), asJsonArray(trueFalse)),
                () -> assertEquals(arrayBuilder().element(1).element(2).element(3).build(), asJsonArray(one23)),
                () -> assertEquals(arrayBuilder().element(4d).element(5d).build(), asJsonArray(four5)),
                () -> assertEquals(arrayBuilder().element("six").element("six").build(), asJsonArray(sixes)),
                () -> assertEquals(arrayBuilder().item(JsonNull.INSTANCE).build(), asJsonArray(nulls))
        );
    }


    @Test
    void testAsInstance() {
        record R(double x, double y, boolean z, int a, char c, long l, float f, byte b, short s,
                 BigInteger bi, BigDecimal bd) {
        }
        var src = objectBuilder()
                .named("x", new JsonNumber(1))
                .named("y", new JsonNumber(2))
                .named("z", JsonBoolean.TRUE)
                .named("s", new JsonNumber(255))
                .named("b", new JsonNumber(127))
                .named("a", new JsonNumber(5))
                .named("c", new JsonNumber('X'))
                .named("l", new JsonNumber(7))
                .named("f", new JsonNumber(3))
                .named("bd", new JsonString("10"))
                .named("bi", new JsonString("2"))
                .build();

        var r12 = asInstance(R.class, src);
        assertAll(
                () -> assertEquals(new R(1d, 2d, true, 5, 'X', 7L, 3f,
                                (byte) 127, (short) 255, BigInteger.TWO, BigDecimal.TEN)
                        , r12)
        );
    }

    @Test
    void testIncompleteRec() {
        record R(int a, int b) {
        }
        var r12 = new R(1, 2);
        var src = objectBuilder().basic("a", 1).build();
        assertAll(
                () -> assertEquals(R.class, asInstance(R.class, src).getClass()),
                () -> assertEquals(r12.a(), asInstance(R.class, src).a()),
                () -> assertNotEquals(r12.b(), asInstance(R.class, src).b()),
                () -> assertEquals(0, asInstance(R.class, src).b())
        );
    }

    @Test
    void testOverStated() {
        record R(int a) {
        }
        var r1 = new R(1);
        var src = objectBuilder()
                .basic("a", 1)
                .basic("b", 2)
                .build();
        assertAll(
                () -> assertEquals(r1, asInstance(R.class, src))
        );
    }

    @Test
    void testIncompleteAndOverStated() {
        record R(int a) {
        }
        var r0 = new R(0);
        var src = objectBuilder()
                .basic("b", 2)
                .build();
        assertAll(
                () -> assertEquals(r0, asInstance(R.class, src))
        );
    }


    @Test
    void testSingle() {
        var src = """
                {"a": [{"b": [5]}]}""";
        try (var rdr = new JsonReader(new StringReader(src))) {
            var elem = rdr.readElement();
            var sngl = single(elem);
            assertAll(
                    () -> assertEquals(JsonBoolean.TRUE, single(JsonBoolean.TRUE)),
                    () -> assertEquals(JsonBoolean.FALSE, single(JsonBoolean.FALSE)),
                    () -> assertEquals(JsonNull.INSTANCE, single(JsonNull.INSTANCE)),
                    () -> assertEquals(new JsonString("hallo"), single(new JsonString("hallo"))),
                    () -> assertEquals(new JsonNumber(4), single(new JsonNumber(4))),
                    () -> assertEquals(new JsonNumber(5), single(new JsonArray(List.of(new JsonNumber(5))))),
                    //() -> assertEquals(new JsonArray(List.of()), single(new JsonArray(List.of()))),
                    //() -> assertEquals(new JsonObject(Map.of()), single(new JsonObject(Map.of()))),
                    () -> assertEquals(new JsonNumber(5), sngl)
            );
        } catch (Throwable t) {
            fail(t);
        }
    }


    @Test
    void testAsJsonObject() {
        record R(int x) {
        }
        record S(String s, boolean b, R r, Object[] array) {
        }
        var r = new R(5);
        var s = new S("hallo", true, r, new Object[]{null});
        var jo = asJsonObject(s);
        System.out.println(jo);
    }
    @Test
    void testAsInstanceFromString() {
        var today = LocalDate.now();
        var jsonToday = new JsonString(today.toString());
        assertEquals(today, asInstance(LocalDate.class, jsonToday));
    }


}
