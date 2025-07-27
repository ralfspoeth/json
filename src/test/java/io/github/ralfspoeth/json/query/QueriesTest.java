package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.github.ralfspoeth.json.Aggregate.arrayBuilder;
import static io.github.ralfspoeth.json.Aggregate.objectBuilder;
import static io.github.ralfspoeth.json.query.Queries.*;
import static org.junit.jupiter.api.Assertions.*;

class QueriesTest {

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
                () -> assertEquals("one", stringValue(new JsonString("one"))),
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
        var obj = objectBuilder()
                .named("e", new JsonString("onet"))
                .build();

        // extracts the member named "e", first three letters of the value, then to upper case
        Function<JsonValue, String> extr = elem -> elem instanceof JsonObject(Map<String, JsonValue> members)
                ? stringValue(members.get("e"), null).substring(0, 3).toUpperCase()
                : null;

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
    void testRec1() {
        record R(String s, boolean b, double d, Object o) {}
        var src = """
                {
                    "s": "a string",
                    "b": true,
                    "d": 5.1,
                    "o": null
                }
                """;
        var jo = Greyson.readValue(src);

        var r = new R(
                stringValue(members(jo).get("s"), ""),
                booleanValue(members(jo).get("b"), false),
                doubleValue(members(jo).get("d"), 0d),
                value(members(jo).get("o"), null)
        );

        assertEquals(new R("a string", true, 5.1d, null), r);
    }

    @Test
    void testList1() {
        record R(double d) {}
        var src = """
                [{"d": 5}, {"d": 6}, {"d": 7}]
                """;
        var ja = Greyson.readValue(src);
        var result = elements(ja)
                .stream()
                .map(Queries::members)
                .map(jo -> jo.get("d"))
                .mapToDouble(e -> doubleValue(e, 0d))
                .mapToObj(R::new)
                .toList();
        assertEquals(List.of(new R(5), new R(6), new R(7)), result);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testValue() {
        var src = """
                {"a": 1
                , "b": true
                , "c": false
                , "d": null
                , "e": [1, 2, 3]
                }""";
        var jo = Greyson.readValue(src);
        assertAll(
                () -> assertInstanceOf(Map.class, value(jo)),
                () -> assertInstanceOf(BigDecimal.class, ((Map<String, ?>) value(jo)).get("a")),
                () -> assertInstanceOf(Boolean.class, ((Map<String, ?>) value(jo)).get("b")),
                () -> assertInstanceOf(Boolean.class, ((Map<String, ?>) value(jo)).get("c")),
                () -> assertNull(((Map<String, ?>) value(jo)).get("d")),
                () -> assertInstanceOf(List.class, ((Map<String, ?>) value(jo)).get("e")),
                () -> assertThrows(NullPointerException.class, () -> value(null))
        );
    }

    @Test
    void testValueDef() {
        assertEquals("hello", value(null, "hello"));
    }

    @Test
    void testPrimitiveArrays() {
        var ja = arrayBuilder().element(0).element(1).build();
        assertAll(
                () -> assertArrayEquals(new int[]{0, 1}, intArray(ja)),
                () -> assertArrayEquals(new boolean[]{false, true}, booleanArray(ja)),
                () -> assertArrayEquals(new byte[]{0, 1}, byteArray(ja)),
                () -> assertArrayEquals(new char[]{0, 1}, charArray(ja)),
                () -> assertArrayEquals(new short[]{0, 1}, shortArray(ja)),
                () -> assertArrayEquals(new long[]{0, 1}, longArray(ja)),
                () -> assertArrayEquals(new float[]{0, 1}, floatArray(ja)),
                () -> assertArrayEquals(new double[]{0, 1}, doubleArray(ja))
        );
    }

}
