package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.*;
import io.github.ralfspoeth.json.io.JsonReader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.Aggregate.arrayBuilder;
import static io.github.ralfspoeth.json.Aggregate.objectBuilder;
import static io.github.ralfspoeth.json.query.Path.intValue;
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
        var singleElem = objectBuilder().named("one", five).build();
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
        var singleElemArray = Aggregate.arrayBuilder().item(five).build();
        var multiElemArray = Aggregate.arrayBuilder().item(five).item(five).item(five).build();
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
                .named("one", new JsonArray(List.of(
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
        var singleElem = objectBuilder().named("oe", five).build();
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
                .named("a",
                        objectBuilder()
                                .named("b", objectBuilder()
                                        .named("c", Basic.of("Zeh"))
                                        .build()
                                ).build()
                )
                .named("x", Basic.of(null))
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
                .builder("a", objectBuilder().builder("b", objectBuilder().named("c", Basic.of(5))))
                .build();
        // when
        var path = of("a/b/c");
        // then
        assertEquals(5, Path.doubleValue(path, obj));
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
        var elem = JsonReader.readElement(src);
        // then
        assertAll(
                () -> assertEquals(1, intValue(of("[0]/a"), elem)),
                () -> assertEquals(3, intValue(of("[0]/c"), elem)),
                () -> assertEquals(7, intValue(of("[1]/d"), elem)),
                () -> assertTrue(Path.booleanValue(of("[5]"), elem)),
                () -> assertEquals(3, intValue(of("[4]"), elem)),
                () -> assertEquals(9, intValue(of("[9]/c"), elem)),
                () -> assertEquals(0, intValue(of("[10]/a"), elem)),
                () -> assertEquals(0, intValue(of("[11]"), elem)),
                () -> assertEquals(1, intValue(of("[-1]/a"), elem)),
                () -> assertEquals(0, intValue(of("[50..60]"), elem))
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
                .builder("bl", objectBuilder().basic("x", 1).basic("y", 2))
                .builder("tr", objectBuilder().basic("x", 3).basic("y", 4))
                .build();
        var obj2 = objectBuilder().named("x1", Basic.of(1)).named("y1", Basic.of(2))
                .named("x2", Basic.of(3)).named("y2", Basic.of(4)).build();
        var arr1 = arrayBuilder().item(Basic.of(1)).item(Basic.of(2)).item(Basic.of(3)).item(Basic.of(4)).build();
        var arr2 = arrayBuilder().item(Basic.of(1)).item(Basic.of(3)).item(Basic.of(2)).item(Basic.of(4)).build();
        // then
        assertAll(
                () -> assertEquals(rect, new Rect(
                                new Point(
                                        intValue(Path.of("bl/x"), obj1),
                                        intValue(Path.of("bl/y"), obj1)
                                ),
                                new Point(
                                        intValue(Path.of("tr/x"), obj1),
                                        intValue(Path.of("tr/y"), obj1)
                                )
                        )
                ), () -> assertEquals(rect, new Rect(
                                new Point(
                                        intValue(Path.of("x1"), obj2),
                                        intValue(Path.of("y1"), obj2)
                                ),
                                new Point(
                                        intValue(Path.of("x2"), obj2),
                                        intValue(Path.of("y2"), obj2)
                                )
                        )
                ), () -> assertEquals(rect, new Rect(
                                new Point(
                                        intValue(Path.of("[0]"), arr1),
                                        intValue(Path.of("[1]"), arr1)
                                ),
                                new Point(
                                        intValue(Path.of("[2]"), arr1),
                                        intValue(Path.of("[3]"), arr1)
                                )
                        )
                ), () -> assertEquals(rect, new Rect(
                                new Point(
                                        intValue(Path.of("[0]"), arr2),
                                        intValue(Path.of("[2]"), arr2)
                                ),
                                new Point(
                                        intValue(Path.of("[1]"), arr2),
                                        intValue(Path.of("[3]"), arr2)
                                )
                        )
                )
        );

    }

}