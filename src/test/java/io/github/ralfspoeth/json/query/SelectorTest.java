package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.data.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.data.Builder.arrayBuilder;
import static io.github.ralfspoeth.json.query.Selector.*;
import static org.junit.jupiter.api.Assertions.*;

class SelectorTest {

    @Test
    void ofNull() {
        // given
        Pattern p = null;
        String s = null;
        // then
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> regex(p)),
                () -> assertThrows(NullPointerException.class, () -> regex(s))
        );
    }

    @Test
    void testAll() {
        // given
        JsonArray ja = arrayBuilder().addBasic(1).addBasic(2).addBasic(3).build();
        JsonObject jo = new JsonObject(Map.of());
        Basic<?> b = JsonNull.INSTANCE;
        // when
        Selector allSelector = all();
        // then
        assertAll(
                () -> assertEquals(ja.elements(), Stream.of(ja).flatMap(allSelector).toList()),
                () -> assertEquals(List.of(jo), Stream.of(jo).flatMap(allSelector).toList()),
                () -> assertEquals(List.of(b), Stream.of(b).flatMap(allSelector).toList())
        );
    }

    /*

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
        return first(value, Selector.of(path));
    }

    private static Optional<JsonValue> first(JsonValue value, Selector selector) {
        return Stream.of(value).flatMap(selector).findFirst();
    }

    private static boolean empty(JsonValue value, String path) {
        return empty(value, Selector.of(path));
    }

    private static boolean empty(JsonValue value, Selector selector) {
        return Stream.of(value).flatMap(selector).findFirst().isEmpty();
    }

    private static Optional<? extends JsonValue> single(JsonValue value, Selector selector) {
        var l = Stream.of(value).flatMap(selector).toList();
        return l.size() == 1 ? Optional.of(l.getFirst()) : Optional.empty();
    }

    @Test
    void ofRange() {
        // given
        var five = Basic.of(5);
        var singleElemArray = Builder.arrayBuilder().add(five).build();
        var multiElemArray = Builder.arrayBuilder().add(five).add(five).add(five).build();
        // when
        var all = Selector.all();
        // then
        assertAll(
                () -> assertEquals(parse("[0..-5]"), parse("[0..-1]")),
                () -> assertTrue(Selector.of("[0..-1]").apply(singleElemArray).allMatch(five::equals)),
                () -> assertTrue(all.apply(singleElemArray).allMatch(five::equals)),
                () -> assertTrue(Selector.of("[0..-1]").apply(multiElemArray).allMatch(five::equals)),
                () -> assertTrue(Selector.of("[0..1]").apply(multiElemArray).allMatch(five::equals)),
                () -> assertTrue(Selector.of("[0..2]").apply(multiElemArray).allMatch(five::equals)),
                () -> assertEquals(2, Selector.of("[0..2]").apply(multiElemArray).count()),
                () -> assertTrue(Selector.of("[0..5]").apply(multiElemArray).allMatch(five::equals)),
                () -> assertEquals(3, Selector.of("[0..5]").apply(multiElemArray).count())

        );
    }

    @Test
    void ofNameRangeRegex() {
        var root = objectBuilder()
                .put("one", new JsonArray(List.of(
                        new JsonObject(Map.of("two", Basic.of(5)))
                )))
                .build();
        var path = parse("one/[0..1]/#t.*o");
        assertEquals(Basic.of(5), path.apply(root).orElseThrow());
    }

    @Test
    void ofRegex() {
        var path = parse("#o.*e");
        var five = Basic.of(5);
        var singleElem = objectBuilder().put("oe", five).build();
        assertEquals(five, path.apply(singleElem).orElseThrow());
    }

    @Test
    void testFlatMap() {
        var array = JsonArray.ofArray(new int[]{1, 2, 3, 4});
        var path = Selector.of("[0..4]");
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
                .flatMap(Selector.of("a"))
                .flatMap(Selector.of("b"))
                .flatMap(Selector.of("c"))
                .toList();
        assertAll(
                () -> assertEquals(new JsonString("Zeh"), l.getFirst()),
                () -> assertEquals(new JsonString("Zeh"), l.getLast())
        );

        var m = Stream.of(obj)
                .flatMap(Selector.of("a/b/c"))
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
        var path = Selector.of("a/b/c");
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
                () -> assertEquals(1, single(elem, Selector.of("[0]/a")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertEquals(3, single(elem, Selector.of("[0]/c")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertEquals(7, single(elem, Selector.of("[1]/d")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertTrue(single(elem, Selector.of("[5]")).flatMap(JsonValue::bool).orElseThrow()),
                () -> assertEquals(3, single(elem, Selector.of("[4]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertEquals(9, single(elem, Selector.of("[9]/c")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertEquals(0, single(elem, Selector.of("[10]/a")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElse(0)),
                () -> assertEquals(0, single(elem, Selector.of("[11]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElse(0)),
                () -> assertEquals(1, single(elem, Selector.of("[-1]/a")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()),
                () -> assertEquals(0, single(elem, Selector.of("[50..60]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElse(0))
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
                                        single(obj1, Selector.of("bl/x")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        single(obj1, Selector.of("bl/y")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                ),
                                new Point(
                                        single(obj1, Selector.of("tr/x")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        single(obj1, Selector.of("tr/y")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                )
                        )
                ), () -> assertEquals(rect, new Rect(
                                new Point(
                                        single(obj2, Selector.of("x1")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        single(obj2, Selector.of("y1")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                ),
                                new Point(
                                        single(obj2, Selector.of("x2")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        single(obj2, Selector.of("y2")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                )
                        )
                ), () -> assertEquals(rect, new Rect(
                                new Point(
                                        single(arr1, Selector.of("[0]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        single(arr1, Selector.of("[1]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                ),
                                new Point(
                                        single(arr1, Selector.of("[2]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        single(arr1, Selector.of("[3]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                )
                        )
                ), () -> assertEquals(rect, new Rect(
                                new Point(
                                        single(arr2, Selector.of("[0]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        single(arr2, Selector.of("[2]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
                                ),
                                new Point(
                                        single(arr2, Selector.of("[1]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                                        single(arr2, Selector.of("[3]")).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow()
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
        var input = IntStream.range(0, 10)
                .mapToObj(_ -> new Point(rnd.nextInt(), rnd.nextInt()))
                .toList();
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
        Pointer x = Pointer.self().member("x"),
                y = Pointer.self().member("y"),
                z = Pointer.self().member("z");
        // then
        assertAll(
                // all elements do have an x and a y but not a z
                /*() -> assertTrue(jsonArray.elements().stream().allMatch(e -> single(e, x).isPresent())),
                () -> assertTrue(jsonArray.elements().stream().allMatch(e -> single(e, y).isPresent())),
                () -> assertTrue(jsonArray.elements().stream().noneMatch(e -> single(e, z).isPresent())),
                // output equals input
                () -> assertEquals(input, jsonArray.elements().stream().map(e -> new Point(
                        single(e, x).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow(),
                        single(e, y).flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow())
                ).toList()),
                () -> assertTrue(single(jsonArray, Selector.of("3/z")).isEmpty())
        );
    }

    @Test
    void testResolveTheFirstTwoThenA() {
        // given
        var root = Selector.all();
        var theFirstTwo = root.range(0, 2);
        var a = Pointer.self().member("a");
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
    void testAs() throws IOException {
        // given
        var src = """
                [
                   "2025-12-31",
                   1, 2, 3, 4, false, null, true, "hello",
                   {"a": 1},
                   {"a": 2, "b": -1}, {"a": 3}, {"a": 4}
                ]""";
        Selector s = Selector.all().regex("[ab]");
        // when
        var l = Greyson.readValue(Reader.of(src))
                .stream()
                .flatMap(s.as(JsonValue::decimal, BigDecimal::intValue))
                .toList();
        // then
        assertAll(
                () -> assertTrue(List.of(-1, 1, 2, 3, 4).containsAll(l)),
                () -> assertTrue(l.containsAll(List.of(-1, 1, 2, 3, 4)))
        );
    }

    @Test
    void testAddress() throws IOException {
        // given
        var src = """
                [
                    {"email": "max.muster@mail.com",
                     "first": "Max", "last": "Muster",
                     "addresses": [
                        {"type":"home", "address":"10 Upper Street", "city": "Gardencity", "country": "DE"},
                        {"type":"vacation", "address":"20 Hideway", "city": "Wellfare", "country": "US"},
                        {"type":"work", "address":"30 Main Road", "city": "Hardwork", "country": "UK"}
                     ]}
                ]
                """;
        // when
        var addresses = Selector.all().range(0, -1)
                .resolve(Pointer.self().member("addresses"))
                .range(0, -1);
        var result = Greyson.readValue(Reader.of(src)).orElseThrow();
        // then
        assertAll(
                () -> assertEquals(3, Stream.of(result).flatMap(addresses).count())/*,
                () -> assertEquals(3, Stream.of(result).flatMap(addresses.member("type")).count()),
                () -> assertEquals(3, Stream.of(result).flatMap(addresses.member("type"))
                        .filter(in(Set.of("home", "work", "vacation"), a -> a.string().orElseThrow()))
                        .count()
                ),
                () -> assertEquals(3, Stream.of(result)
                        .flatMap(addresses.member("type").as(JsonValue::string, identity()))
                        .filter(in(Set.of("home", "work", "vacation"), identity()))
                        .count()
                ),
                () -> assertEquals(3, Stream.of(result)
                        .flatMap(addresses.member("country").as(JsonValue::string, Locale::of))
                        .filter(in(Set.of("de", "us", "uk"), Locale::getLanguage))
                        .count()
                )
        );
    }

    private static final JsonArray TEST_ARRAY = arrayBuilder()
            .add(JsonNull.INSTANCE)
            .add(JsonBoolean.TRUE)
            .add(JsonBoolean.FALSE)
            .add(new JsonNumber(BigDecimal.valueOf(2147483647))) // 2^31-1
            .add(new JsonNumber(BigDecimal.valueOf(2147483648L))) // 2^31
            .add(new JsonNumber(BigDecimal.valueOf(9223372036854775807L))) // 2^63-1
            .add(new JsonNumber(BigDecimal.valueOf(9223372036854775807L).add(BigDecimal.ONE))) // 2^63
            .addBasic(0.5d)
            .addBasic("")
            .addBasic("Hello World")
            .build();


    @Test
    void testValues() {
        assertAll(
                () -> assertDoesNotThrow(() -> TEST_ARRAY.elements().stream().flatMap(Selector.all().as(JsonValue::decimal, BigDecimal::intValue))),
                () -> assertDoesNotThrow(() -> TEST_ARRAY.elements().stream().flatMap(Selector.all().as(JsonValue::decimal, BigDecimal::longValue))),
                () -> assertDoesNotThrow(() -> TEST_ARRAY.elements().stream().flatMap(Selector.all().as(JsonValue::decimal, BigDecimal::doubleValue))),
                () -> assertDoesNotThrow(() -> Pointer.self().index(3).intValueExact(TEST_ARRAY)),
                () -> assertThrows(ArithmeticException.class, () -> Pointer.self().index(4).intValueExact(TEST_ARRAY)),
                () -> assertThrows(ArithmeticException.class, () -> Pointer.self().index(5).intValueExact(TEST_ARRAY)),
                () -> assertThrows(ArithmeticException.class, () -> Pointer.self().index(6).intValueExact(TEST_ARRAY)),
                () -> assertThrows(ArithmeticException.class, () -> Pointer.self().index(7).intValueExact(TEST_ARRAY)),
                () -> assertDoesNotThrow(() -> Pointer.self().index(5).longValueExact(TEST_ARRAY)),
                () -> assertDoesNotThrow(() -> Pointer.self().index(6).longValue(TEST_ARRAY)),
                () -> assertDoesNotThrow(() -> Pointer.self().index(7).longValue(TEST_ARRAY)),
                () -> assertDoesNotThrow(() -> Pointer.self().index(7).doubleValue(TEST_ARRAY)),
                () -> assertThrows(ArithmeticException.class, () -> Pointer.self().index(6).longValueExact(TEST_ARRAY)),
                () -> assertThrows(ArithmeticException.class, () -> Pointer.self().index(7).longValueExact(TEST_ARRAY)),
                () -> assertTrue(Pointer.self().index(1).booleanValue(TEST_ARRAY).orElseThrow()),
                () -> assertFalse(Pointer.self().index(2).booleanValue(TEST_ARRAY).orElseThrow()),
                () -> assertTrue(Pointer.self().index(0).apply(TEST_ARRAY).filter(JsonNull.class::isInstance).isPresent()),
                () -> assertEquals("", Pointer.self().index(8).stringValue(TEST_ARRAY).orElseThrow()),
                () -> assertEquals("Hello World", Pointer.self().index(9).stringValue(TEST_ARRAY).orElseThrow())
        );
    }*/
}
