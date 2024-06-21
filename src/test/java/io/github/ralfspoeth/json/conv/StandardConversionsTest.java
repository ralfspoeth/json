package io.github.ralfspoeth.json.conv;

import io.github.ralfspoeth.json.*;
import io.github.ralfspoeth.json.io.JsonReader;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

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
                () -> assertEquals(arrayBuilder().item(JsonBoolean.TRUE).item(JsonBoolean.FALSE).build(), JsonArray.of(trueFalse)),
                () -> assertEquals(arrayBuilder().element(1).element(2).element(3).build(), JsonArray.of(one23)),
                () -> assertEquals(arrayBuilder().element(4d).element(5d).build(), JsonArray.of(four5)),
                () -> assertEquals(arrayBuilder().element("six").element("six").build(), JsonArray.of(sixes)),
                () -> assertEquals(arrayBuilder().item(JsonNull.INSTANCE).build(), JsonArray.of(nulls))
        );
    }


    @Test
    void testAs() {
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

        var r12 = as(R.class, src);
        assertAll(
                () -> assertEquals(
                        new R(1d, 2d, true, 5, 'X', 7L, 3f,
                                (byte) 127, (short) 255
                                , BigInteger.TWO, BigDecimal.TEN
                        ), r12)
        );
    }

    @Test
    void testIncompleteRec() {
        record R(int a, int b) {
        }
        var r12 = new R(1, 2);
        var src = objectBuilder().basic("a", 1).build();
        assertAll(
                () -> assertEquals(R.class, as(R.class, src).getClass()),
                () -> assertEquals(r12.a(), as(R.class, src).a()),
                () -> assertNotEquals(r12.b(), as(R.class, src).b()),
                () -> assertEquals(0, as(R.class, src).b())
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
                () -> assertEquals(r1, as(R.class, src))
        );
    }

    @Test
    void testIncompleteAndOverStated() {
        record R(int a) {
        }
        record S(int b) {
        }
        record T(int a, int b) {
        }
        var r0 = new R(0);
        var s2 = new S(2);
        var t02 = new T(0, 2);
        var src = objectBuilder()
                .basic("b", 2)
                .basic("c", true)
                .build();
        assertAll(
                () -> assertEquals(r0, as(R.class, src)),
                () -> assertEquals(s2, as(S.class, src)),
                () -> assertEquals(t02, as(T.class, src))
        );
    }


    @Test
    void testNested() {
        record R(int a) {
        }
        record S(int b, R r) {
        }
        record T(S[] esses) {
        }

        // r's
        var r0 = new R(0);
        var r1 = new R(1);
        var r2 = new R(2);
        // s'ses
        var s0r2 = new S(0, r2);
        var s1r1 = new S(1, r1);
        var s2r0 = new S(2, r0);
        // t
        var t = new T(new S[]{s0r2, s1r1, s2r0});

        // source
        var src = """
                {"esses": [
                    {"b": 0, "r": {"a": 2}},
                    {"b": 1, "r": {"a": 1}},
                    {"b": 2, "r": {"a": 0}}
                ], "quark": null}
                """;
        var ret = JsonReader.readElement(src);
        var retConvd = StandardConversions.as(T.class, ret);

        // assertions
        assertAll(
                () -> assertEquals(t.esses.length, retConvd.esses.length),
                () -> assertArrayEquals(t.esses, retConvd.esses)
        );
    }

    @Test
    void testAsFromString() {
        var today = LocalDate.now();
        assertAll(
                () -> assertEquals(today, as(LocalDate.class, new JsonString(today.toString()))),
                () -> assertEquals(today, as(LocalDate.class, Basic.of(today)))

        );
    }


    @Test
    void testDeconstruct() {

        // arbitrary class
        class Demo {
            int X;
            double y;
            List<Integer> ints;

            Demo(int a, double b, int[] array) {
                this.X = a;
                this.y = b;
                this.ints = IntStream.of(array).boxed().toList();
            }

            @Override
            public String toString() {
                return "Demo{" +
                        "X=" + X +
                        ", y=" + y +
                        ", ints=" + ints +
                        '}';
            }
        }

        var src = """
                {"a": true,
                 "b": null,
                 "c": 0,
                 "d": "D",
                 "e": [1, 2, 3],
                 "f": {"x":1, "y":2}}
                """;
        var jo = JsonReader.readElement(src);
        var result = switch (jo) {
            case JsonObject(Map<String, Element> m) -> new Demo(
                    StandardConversions.intValue(m.get("c"), 0),
                    StandardConversions.doubleValue(m.get("a"), 0d),
                    ((JsonArray) m.get("e")).elements().stream()
                            .mapToInt(e -> (int) ((JsonNumber) e).numVal())
                            .toArray()
            );
            default -> throw new AssertionError();
        };
        var expected = new Demo(0, 1.0, new int[]{1, 2, 3});
        assertAll(
                () -> assertEquals(expected.X, result.X),
                () -> assertEquals(expected.y, result.y),
                () -> assertEquals(expected.ints, result.ints)
        );
    }

    @Test
    void testJsonNullToObject() {
        assertAll(
                () -> assertNull(as(Object.class, JsonNull.INSTANCE)),
                () -> assertNull(as(Record.class, JsonNull.INSTANCE))
        );
    }
}
