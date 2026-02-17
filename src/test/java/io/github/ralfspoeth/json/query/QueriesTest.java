package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.*;
import io.github.ralfspoeth.json.data.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static io.github.ralfspoeth.json.data.Builder.arrayBuilder;
import static io.github.ralfspoeth.json.query.Queries.*;
import static org.junit.jupiter.api.Assertions.*;

class QueriesTest {

    @Test
    void testAsJsonArray() {
        boolean[] trueFalse = {true, false};
        int[] one23 = {1, 2, 3};
        double[] four5 = {4.d, 5.d};
        String[] sixes = {"six", "six"};
        Object[] nulls = {null};
        assertAll(
                () -> assertEquals(arrayBuilder().add(JsonBoolean.TRUE).add(JsonBoolean.FALSE).build(), JsonArray.of(trueFalse)),
                () -> assertEquals(arrayBuilder().addBasic(1).addBasic(2).addBasic(3).build(), JsonArray.of(one23)),
                () -> assertEquals(arrayBuilder().addBasic(4d).addBasic(5d).build(), JsonArray.of(four5)),
                () -> assertEquals(arrayBuilder().addBasic("six").addBasic("six").build(), JsonArray.of(sixes)),
                () -> assertEquals(arrayBuilder().add(JsonNull.INSTANCE).build(), JsonArray.of(nulls))
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
        var jo = Greyson.read(src).orElseThrow();

        var r = new R(
                jo.get("s").flatMap(JsonValue::string).orElse(""),
                jo.get("b").flatMap(JsonValue::bool).orElse(false),
                jo.get("d").flatMap(JsonValue::decimal).map(BigDecimal::doubleValue).orElse(0d),
                jo.get("o").map(Queries::asObject).orElse(null)
        );

        assertEquals(new R("a string", true, 5.1d, null), r);
    }

    @Test
    void testList1() {
        record R(double d) {}
        var src = """
                [{"d": 5}, {"d": 6}, {"d": 7}]
                """;
        var ja = Greyson.read(src).orElseThrow();
        var result = ja.elements()
                .stream()
                .map(JsonValue::members)
                .map(jo -> jo.get("d"))
                .mapToDouble(e -> e.decimal(BigDecimal.ZERO).doubleValue())
                .mapToObj(R::new)
                .toList();
        assertEquals(List.of(new R(5), new R(6), new R(7)), result);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testAsObject() {
        var src = """
                {"a": 1
                , "b": true
                , "c": false
                , "d": null
                , "e": [1, 2, 3]
                }""";
        var jo = Greyson.read(src).orElseThrow();
        assertAll(
                () -> assertInstanceOf(Map.class, asObject(jo)),
                () -> assertInstanceOf(BigDecimal.class, ((Map<String, ?>) asObject(jo)).get("a")),
                () -> assertInstanceOf(Boolean.class, ((Map<String, ?>) asObject(jo)).get("b")),
                () -> assertInstanceOf(Boolean.class, ((Map<String, ?>) asObject(jo)).get("c")),
                () -> assertNull(((Map<String, ?>) asObject(jo)).get("d")),
                () -> assertInstanceOf(List.class, ((Map<String, ?>) asObject(jo)).get("e")),
                () -> assertThrows(NullPointerException.class, () -> asObject(null))
        );
    }

    @Test
    void testPrimitiveArrays() {
        var ja = arrayBuilder().addBasic(0).addBasic(1).build();
        assertAll(
                () -> assertArrayEquals(new int[]{0, 1}, intArray(ja)),
                () -> assertArrayEquals(new long[]{0, 1}, longArray(ja)),
                () -> assertArrayEquals(new double[]{0, 1}, doubleArray(ja))
        );
    }

}
