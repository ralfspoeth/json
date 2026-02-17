package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.*;
import io.github.ralfspoeth.json.data.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.data.Builder.arrayBuilder;
import static io.github.ralfspoeth.json.data.Builder.objectBuilder;
import static io.github.ralfspoeth.json.query.Path.of;
import static org.junit.jupiter.api.Assertions.*;

class PathTest {

    @Test
    void ofEmpty() {
        assertThrows(NullPointerException.class, () -> of(null));
    }

    @Test
    void ofSingle() {
        var five = Basic.of(5);
        var singleElem = objectBuilder().put("one", five).build();
        assertAll(
                () -> assertEquals(of("one"), of("one")),
                () -> assertTrue(of("one").apply(singleElem).allMatch(five::equals))
        );
    }

    @Test
    void ofIndex() {
        // given
        var one = Basic.of(1);
        var two = Basic.of(2);
        var three = Basic.of(3);
        // when
        var a = new JsonArray(List.of(one, two, three));
        // then
        assertAll(
                () -> assertEquals(one, first(a, "[0]")),
                () -> assertEquals(two, first(a, "[1]")),
                () -> assertEquals(three, first(a, "[2]")),
                () -> assertEquals(one, first(a, "[-3]")),
                () -> assertEquals(two, first(a, "[-2]")),
                () -> assertEquals(three, first(a, "[-1]")),
                () -> assertTrue(empty(a, "[-4]")),
                () -> assertTrue(empty(a, "[3]"))
        );
    }

    private static JsonValue first(JsonValue root, String path) {
        return of(path).first(root).orElseThrow();
    }

    private static boolean empty(JsonValue root, String path) {
        return of(path).first(root).isEmpty();
    }


    @Test
    void ofRange() {
        var five = Basic.of(5);
        var singleElemArray = Builder.arrayBuilder().add(five).build();
        var multiElemArray = Builder.arrayBuilder().add(five).add(five).add(five).build();
        assertAll(
                () -> assertEquals(of("[0..-5]"), of("[0..-1]")),
                () -> assertTrue(of("[0..-1]").apply(singleElemArray).allMatch(five::equals)),
                () -> assertTrue(of("[0..-1]").apply(multiElemArray).allMatch(five::equals)),
                () -> assertTrue(of("[0..1]").apply(multiElemArray).allMatch(five::equals)),
                () -> assertTrue(of("[0..2]").apply(multiElemArray).allMatch(five::equals)),
                () -> assertEquals(2, of("[0..2]").apply(multiElemArray).count()),
                () -> assertTrue(of("[0..5]").apply(multiElemArray).allMatch(five::equals)),
                () -> assertEquals(3, of("[0..5]").apply(multiElemArray).count())

        );
    }

    @Test
    void ofNameRangeRegex() {
        var root = objectBuilder()
                .put("one", new JsonArray(List.of(
                        new JsonObject(Map.of("two", Basic.of(5)))
                )))
                .build();
        var path = of("one/[0..1]/#t.*o");
        assertEquals(Basic.of(5), path.apply(root).findFirst().orElseThrow());
    }

    @Test
    void ofRegex() {
        var path = of("#o.*e");
        var five = Basic.of(5);
        var singleElem = objectBuilder().put("oe", five).build();
        assertEquals(five, path.apply(singleElem).findFirst().orElseThrow());
    }

    @Test
    void testFlatMap() {
        var array = JsonArray.ofArray(new int[]{1, 2, 3, 4});
        var path = of("[0..4]");
        assertTrue(Stream.of(array)
                .flatMap(path)
                .allMatch(JsonNumber.class::isInstance));

        var obj = objectBuilder()
                .put("a",
                        objectBuilder()
                                .put("b", objectBuilder()
                                        .put("c", Basic.of("Zeh"))
                                        .build()
                                ).build()
                )
                .put("x", Basic.of(null))
                .build();

        var l = Stream.of(obj)
                .flatMap(of("a"))
                .flatMap(of("b"))
                .flatMap(of("c"))
                .toList();
        assertAll(
                () -> assertEquals(new JsonString("Zeh"), l.getFirst()),
                () -> assertEquals(new JsonString("Zeh"), l.getLast())
        );

        var m = Stream.of(obj)
                .flatMap(of("a/b/c"))
                .toList();
        assertAll(
                () -> assertEquals(new JsonString("Zeh"), m.getFirst()),
                () -> assertEquals(new JsonString("Zeh"), m.getLast())
        );
    }


    @Test
    void testSingle() {
        // given
        var obj = objectBuilder()
                .put("a", objectBuilder().put("b", objectBuilder().putBasic("c", 5).build()).build())
                .build();
        // when
        var path = of("a/b/c");
        // then
        assertEquals(5, path.single(obj).flatMap(JsonValue::decimal).map(BigDecimal::doubleValue).orElseThrow());
    }

    @Test
    void testComplex() {
        // given
        var src = """
                [{"a": 1, "b": 2, "c": 3}, {"a": 4, "b": 5, "c": 6, "d": 7},
                 1, 2, 3,
                 true, false, null,
                 [[[[]]]],
                 {"a": 1, "b": 2, "c": 9}
                ]""";
        // when
        var elem = Greyson.read(src).orElseThrow();
        // then
        assertAll(
                () -> assertEquals(1, of("[0]/a").single(elem).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertEquals(3, of("[0]/c").single(elem).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertEquals(7, of("[1]/d").single(elem).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertTrue(of("[5]").single(elem).flatMap(JsonValue::bool).orElseThrow()),
                () -> assertEquals(3, of("[4]").single(elem).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertEquals(9, of("[9]/c").single(elem).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertEquals(0, of("[10]/a").single(elem).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElse(0)),
                () -> assertEquals(0, of("[11]").single(elem).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElse(0)),
                () -> assertEquals(1, of("[-1]/a").single(elem).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertEquals(0, of("[50..60]").single(elem).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElse(0))
        );
    }

    @Test
    void testRect() {
        // given
        record Point(int x, int y) {}
        record Rect(Point bottomLeft, Point topRight) {}
        var rect = new Rect(new Point(1, 2), new Point(3, 4));
        // when
        var obj1 = objectBuilder()
                .put("bl", objectBuilder().putBasic("x", 1).putBasic("y", 2))
                .put("tr", objectBuilder().putBasic("x", 3).putBasic("y", 4))
                .build();
        var obj2 = objectBuilder().put("x1", Basic.of(1)).put("y1", Basic.of(2))
                .put("x2", Basic.of(3)).put("y2", Basic.of(4)).build();
        var arr1 = arrayBuilder().add(Basic.of(1)).add(Basic.of(2)).add(Basic.of(3)).add(Basic.of(4)).build();
        var arr2 = arrayBuilder().add(Basic.of(1)).add(Basic.of(3)).add(Basic.of(2)).add(Basic.of(4)).build();
        // then
        assertAll(
                () -> assertEquals(rect, new Rect(
                                new Point(
                                        of("bl/x").single(obj1).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        of("bl/y").single(obj1).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                ),
                                new Point(
                                        of("tr/x").single(obj1).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        of("tr/y").single(obj1).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                )
                        )
                ), () -> assertEquals(rect, new Rect(
                                new Point(
                                        of("x1").single(obj2).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        of("y1").single(obj2).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                ),
                                new Point(
                                        of("x2").single(obj2).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        of("y2").single(obj2).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                )
                        )
                ), () -> assertEquals(rect, new Rect(
                                new Point(
                                        of("[0]").single(arr1).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        of("[1]").single(arr1).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                ),
                                new Point(
                                        of("[2]").single(arr1).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        of("[3]").single(arr1).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                )
                        )
                ), () -> assertEquals(rect, new Rect(
                                new Point(
                                        of("[0]").single(arr2).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        of("[2]").single(arr2).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                ),
                                new Point(
                                        of("[1]").single(arr2).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        of("[3]").single(arr2).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                )
                        )
                )
        );

    }

}