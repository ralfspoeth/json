package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.Greyson;
import io.github.ralfspoeth.json.data.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.data.Builder.arrayBuilder;
import static io.github.ralfspoeth.json.data.Builder.objectBuilder;
import static io.github.ralfspoeth.json.query.Path.of;
import static io.github.ralfspoeth.json.query.Path.root;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class PathTest {

    @Test
    void ofNull() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> of(null)),
                () -> assertThrows(NullPointerException.class, () -> root().member(null)),
                () -> assertThrows(NullPointerException.class, () -> root().regex(null))
        );
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
                () -> assertEquals(one, first(a, "[0]").orElseThrow()),
                () -> assertEquals(two, first(a, "[1]").orElseThrow()),
                () -> assertEquals(three, first(a, "[2]").orElseThrow()),
                () -> assertEquals(one, first(a, "[-3]").orElseThrow()),
                () -> assertEquals(two, first(a, "[-2]").orElseThrow()),
                () -> assertEquals(three, first(a, "[-1]").orElseThrow()),
                () -> assertTrue(empty(a, "[-4]")),
                () -> assertTrue(empty(a, "[3]"))
        );
    }

    private static Optional<JsonValue> first(JsonValue value, String path) {
        return first(value, Path.of(path));
    }

    private static Optional<JsonValue> first(JsonValue value, Path path) {
        return Stream.of(value).flatMap(path).findFirst();
    }

    private static boolean empty(JsonValue value, String path) {
        return empty(value, Path.of(path));
    }

    private static boolean empty(JsonValue value, Path path) {
        return Stream.of(value).flatMap(path).findFirst().isEmpty();
    }

    private static Optional<? extends JsonValue> single(JsonValue value, String path) {
        return single(value, Path.of(path));
    }

    private static Optional<? extends JsonValue> single(JsonValue value, Path path) {
        var l = Stream.of(value).flatMap(path).toList();
        return l.size() == 1 ? Optional.of(l.getFirst()) : Optional.empty();
    }

    @Test
    void ofRange() {
        // given
        var five = Basic.of(5);
        var singleElemArray = Builder.arrayBuilder().add(five).build();
        var multiElemArray = Builder.arrayBuilder().add(five).add(five).add(five).build();
        // when
        var all = Path.root().range(0, -1);
        var o1 = Path.root().range(0, 1);
        var o2 = Path.root().range(0, 2);
        // then
        assertAll(
                () -> assertEquals(of("[0..-5]"), of("[0..-1]")),
                () -> assertTrue(of("[0..-1]").apply(singleElemArray).allMatch(five::equals)),
                () -> assertTrue(all.apply(singleElemArray).allMatch(five::equals)),
                () -> assertTrue(of("[0..-1]").apply(multiElemArray).allMatch(five::equals)),
                () -> assertTrue(of("[0..1]").apply(multiElemArray).allMatch(five::equals)),
                () -> assertTrue(o1.apply(multiElemArray).allMatch(five::equals)),
                () -> assertTrue(of("[0..2]").apply(multiElemArray).allMatch(five::equals)),
                () -> assertTrue(o2.apply(multiElemArray).allMatch(five::equals)),
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
        assertEquals(5, first(obj, path).flatMap(JsonValue::decimal).map(BigDecimal::doubleValue).orElseThrow());
    }

    @Test
    void testComplex() throws IOException {
        // given
        var src = """
                [{"a": 1, "b": 2, "c": 3}, {"a": 4, "b": 5, "c": 6, "d": 7},
                 1, 2, 3,
                 true, false, null,
                 [[[[]]]],
                 {"a": 1, "b": 2, "c": 9}
                ]""";
        // when
        var elem = Greyson.readValue(Reader.of(src)).orElseThrow();
        // then
        assertAll(
                () -> assertEquals(1, single(elem, of("[0]/a")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertEquals(3, single(elem, of("[0]/c")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertEquals(7, single(elem, of("[1]/d")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertTrue(single(elem, of("[5]")).flatMap(JsonValue::bool).orElseThrow()),
                () -> assertEquals(3, single(elem, of("[4]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertEquals(9, single(elem, of("[9]/c")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertEquals(0, single(elem, of("[10]/a")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElse(0)),
                () -> assertEquals(0, single(elem, of("[11]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElse(0)),
                () -> assertEquals(1, single(elem, of("[-1]/a")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertEquals(0, single(elem, of("[50..60]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElse(0))
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
                                        single(obj1, of("bl/x")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        single(obj1, of("bl/y")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                ),
                                new Point(
                                        single(obj1, of("tr/x")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        single(obj1, of("tr/y")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                )
                        )
                ), () -> assertEquals(rect, new Rect(
                                new Point(
                                        single(obj2, of("x1")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        single(obj2, of("y1")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                ),
                                new Point(
                                        single(obj2, of("x2")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        single(obj2, of("y2")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                )
                        )
                ), () -> assertEquals(rect, new Rect(
                                new Point(
                                        single(arr1, of("[0]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        single(arr1, of("[1]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                ),
                                new Point(
                                        single(arr1, of("[2]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        single(arr1, of("[3]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                )
                        )
                ), () -> assertEquals(rect, new Rect(
                                new Point(
                                        single(arr2, of("[0]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        single(arr2, of("[2]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                ),
                                new Point(
                                        single(arr2, of("[1]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        single(arr2, of("[3]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                )
                        )
                )
        );
    }

    @Test
    void testArray() {
        // given
        record Point(int x, int y) {}
        var rnd = ThreadLocalRandom.current();
        var input = IntStream.range(0, 10).mapToObj(_ -> new Point(rnd.nextInt(), rnd.nextInt())).toList();
        var jsonArray = input
                .stream()
                .map(p -> objectBuilder().putBasic("x", p.x).putBasic("y", p.y).build())
                .collect(Queries.toJsonArray());
        // make sure the "given" is what we think it is
        assert jsonArray.size() == 10;
        assert jsonArray.elements()
                .stream()
                .allMatch(e -> e instanceof JsonObject(var members)
                        && members.size() == 2
                        && Set.of("x", "y").containsAll(members.keySet())
                );
        // when
        Path x = Path.root().member("x"), y = Path.root().member("y"), z = Path.root().member("z");
        // then
        assertAll(
                // all elements do have an x and a y but not a z
                () -> assertTrue(jsonArray.elements().stream().allMatch(e -> single(e, x).isPresent())),
                () -> assertTrue(jsonArray.elements().stream().allMatch(e -> single(e, y).isPresent())),
                () -> assertTrue(jsonArray.elements().stream().noneMatch(e -> single(e, z).isPresent())),
                // output equals input
                () -> assertEquals(input, jsonArray.elements().stream().map(e -> new Point(
                        single(e, x).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                        single(e, y).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow())
                ).toList()),
                () -> assertInstanceOf(JsonObject.class, single(jsonArray, Path.root().index(7)).orElseThrow()),
                () -> assertInstanceOf(JsonNumber.class, single(jsonArray, Path.root().index(8).resolve(x)).orElseThrow()),
                () -> assertInstanceOf(JsonNumber.class, single(jsonArray, Path.root().index(2).resolve(y)).orElseThrow()),
                () -> assertTrue(single(jsonArray, Path.root().index(3).resolve(z)).isEmpty()),
                () -> assertTrue(single(jsonArray, Path.of("3/z")).isEmpty())
        );
    }

    @Test
    void testResolveTheFirstTwoThenA() {
        // given
        var root = Path.root();
        var theFirstTwo = root.range(0, 2);
        var a = root.member("a");
        var value = arrayBuilder()
                .add(objectBuilder().putBasic("a", 1))
                .add(objectBuilder().putBasic("a", 2))
                .build(); // [{"a": 1}, {"a": 2}]
        // when
        var aRelativeToTheFirstTwo = theFirstTwo.resolve(a);
        // then
        assertAll(
                () -> assertEquals(BigDecimal.ONE, Stream.of(value)
                        .flatMap(aRelativeToTheFirstTwo)
                        .findFirst()
                        .map(jv -> jv.decimal().orElse(ZERO))
                        .orElseThrow()
                ),
                () -> assertEquals(BigDecimal.TWO, Stream.of(value)
                        .flatMap(aRelativeToTheFirstTwo)
                        .map(jv -> jv.decimal().orElse(ZERO))
                        .toList()
                        .getLast()
                ),
                () -> assertArrayEquals(new JsonValue[]{Basic.of(1), Basic.of(2)},
                        Stream.of(value).flatMap(aRelativeToTheFirstTwo).toArray()
                )
        );
    }

    @Test
    void testFirstWithFunction() throws IOException {
        // given
        var src = """
                    ["2025-12-31"]
                """;
        // when
        Path p = Path.root().index(0);
        var ldt = Greyson.readValue(Reader.of(src))
                .flatMap(p.first(v -> v.string().map(LocalDate::parse)))
                .orElseThrow();

        var alt1 = Greyson.readValue(Reader.of(src))
                .flatMap(p.first(JsonValue::string))
                .map(LocalDate::parse)
                .orElseThrow();

        var alt2 = Greyson.readValue(Reader.of(src))
                .flatMap(p.first())
                .flatMap(JsonValue::string)
                .map(LocalDate::parse)
                .orElseThrow();

        // then
        assertAll(
                () -> assertEquals(LocalDate.of(2025, 12, 31), ldt),
                () -> assertEquals(LocalDate.of(2025, 12, 31), alt1),
                () -> assertEquals(LocalDate.of(2025, 12, 31), alt2)
        );
    }
}