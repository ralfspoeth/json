package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.Aggregate.arrayBuilder;
import static io.github.ralfspoeth.json.Greyson.read;
import static io.github.ralfspoeth.json.query.Queries.members;
import static io.github.ralfspoeth.json.query.Validation.*;
import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {

    @Test
    void testRequiredKeys() {
        // given: obj with two keys "a" and "b"
        var obj = new JsonObject(Map.of("a", Basic.of(5), "b", Basic.of(7)));
        // when
        var keys1 = Set.of("a", "b", "c");
        var keys2 = Set.of("a");
        var keys3 = Set.of("a", "b");
        // then
        assertAll(
                () -> assertFalse(required(keys1).test(obj)),
                () -> assertTrue(required(keys2).test(obj)),
                () -> assertTrue(required(keys3).test(obj))
        );
    }

    @Test
    void testMatchesObject() {
        // given
        var obj = new JsonObject(Map.of("a", Basic.of(5), "b", Basic.of(true), "c", Basic.of("zeh")));
        // when
        var struc1 = matches(Map.of(
                "a", is(JsonNumber.class),
                "b", is(JsonBoolean.class),
                "c", regex("[a-z]+")
        )); // all keys included
        var struc2 = matches(Map.of("a", Basic.of(5))); // incomplete
        var struc3 = matches(Map.of(
                "a", is(JsonString.class), // not a string, must fail
                "b", Basic.of(false) // not false but true
        ));
        // then
        assertAll(
                () -> assertTrue(struc1.test(obj)),
                () -> assertTrue(struc2.test(obj)),
                () -> assertThrows(ValidationException.class, ()->matchesOrThrow(struc3).test(obj))
        );
    }

    @Test
    void testRequiredMap() {
        // given
        Map<String, JsonValue> data = Map.of("a", Basic.of(5), "b", Basic.of(true));
        var obj = new JsonObject(data);
        // when
        var struc1 = required(Map.of("a", is(JsonNumber.class), "b", is(JsonBoolean.class))); // keys and types
        var struc2 = required(Map.of("a", Basic.of(5), "b", Basic.of(true))); // exact map
        var struc3 = required(Map.of("c", Basic.of(null))); // key c missing
        var struc4 = required(Map.of("a", x -> false, "b", x -> false));
        // then
        assertAll(
                () -> assertTrue(struc1.test(obj)),
                () -> assertTrue(struc2.test(obj)),
                () -> assertFalse(struc3.test(obj)),
                () -> assertFalse(struc4.test(obj))
        );
    }

    @Test
    void testAll() {
        // given
        var array = new JsonArray(IntStream.of(1, 2, 3, 4, 5)
                .boxed()
                .map(Basic::of)
                .map(JsonValue.class::cast)
                .toList());
        // when
        var number = is(JsonNumber.class);
        var string = is(JsonString.class);
        // then
        assertAll(
                () -> assertTrue(all(number).test(array)),
                () -> assertFalse(all(string).test(array))
        );
    }

    @Test
    void testSimpleArrayTypeStructure() {
        // given
        var array = arrayBuilder().basic(1).basic(true).build();
        // when
        var structure = matchesTypesOf(array);
        var matching = arrayBuilder().basic(2).basic(true).build();
        // then
        assertAll(
                () -> assertTrue(structure.test(matching))
        );
    }


    @Test
    void testMatchesTypes() {
        // given is a list of
        // a boolean, the null instance, a number, a string, a map with keys "a" and "b" to a boolean and a number, a list of 9
        var array = new JsonArray(List.of(
                Basic.of(true),
                Basic.of(null),
                Basic.of(5),
                Basic.of("hello"),
                new JsonObject(Map.of("a", Basic.of(false), "b", Basic.of(7))),
                new JsonArray(List.of(Basic.of(9)))
        ));
        // when
        var arrayTypeStructure = matchesTypesOf(array);
        // then
        assertAll(
                () -> assertTrue(arrayTypeStructure.test(new JsonArray(List.of(
                        Basic.of(false), Basic.of(null), Basic.of(-5), Basic.of("ola"),
                        new JsonObject(Map.of("a", Basic.of(true), "b", Basic.of(-7))),
                        new JsonArray(List.of(Basic.of(-9)))
                ))))
        );
    }


    @Test
    void testAny() {
        // given
        var array = new JsonArray(Stream.concat(IntStream.of(1, 2, 3, 4, 5)
                        .boxed()
                        .map(Basic::of)
                        .map(JsonValue.class::cast),
                Stream.of(Basic.of(null), Basic.of(true))
        ).toList());
        // when
        var number = is(JsonNumber.class);
        var string = is(JsonString.class);
        var bool = is(JsonBoolean.class);

        array.elements().stream().filter(string).forEach(System.out::println);

        // then
        assertAll( // there are...
                () -> assertTrue(any(number).test(array)), // ...numbers
                () -> assertTrue(any(bool).test(array)), // ... one boolean
                () -> assertTrue(any(Basic.of(null)).test(array)), //... a null
                () -> assertFalse(any(string).test(array)) // ... but no strings
        );
    }

    @Test
    void testMatchesOrThrow() {
        // given
        var value = Basic.of(true);
        // then
        assertAll(
                () -> assertThrows(ValidationException.class, () -> matchesOrThrow(Basic.of(false)).test(value)),
                () -> assertDoesNotThrow(() -> matchesOrThrow(Basic.of(true)).test(value))
        );
    }

    @Test
    void testSimpleExplain() {
        // given
        var src = """
                [{"x":10}, 1, true, null]""";
        // when
        var arr = read(src);
        // then
        assertAll(
                () -> assertEquals(10, arr.stream()
                        .filter(is(JsonArray.class).and(any(is(JsonObject.class))))
                        .map(Queries::elements)
                        .flatMap(List::stream)
                        .filter(is(JsonObject.class))
                        .mapToInt(v -> Path.intValue(Path.of("x"), v))
                        .findFirst().orElseThrow()
                ),
                () -> assertDoesNotThrow(() -> arr.filter(matchesOrThrow(is(JsonArray.class)))),
                () -> assertThrows(ValidationException.class, () -> arr.filter(matchesOrThrow(is(JsonObject.class)))),
                () -> assertTrue(arr.filter(matches(List.of(is(JsonObject.class), always(), always(), always()))).isPresent())
        );
    }

    @Test
    void testStandardInvocationChain() {
        // given
        record Point(int x, int y){}
        var src = """
                [{"x":10}, {"x":11, "y": -11}, {"y":12},
                 {"y":13, "z":14, "str":"hello"}]""";
        // when
        Optional<JsonValue> array = read(src);
        Map<String, Predicate<JsonValue>> structureOfObjects = Map.of(
                "x", is(JsonNumber.class),
                "y", is(JsonNumber.class),
                "z", is(JsonNumber.class)
        );
        // then
        var points = array
                .filter(all(is(JsonObject.class)
                        .and(matches(structureOfObjects))
                        .and(o -> Stream.of("x", "y").anyMatch(members(o).keySet()::contains))))
                .stream()
                .map(Queries::elements)
                .flatMap(Collection::stream)
                .map(jv -> new Point(
                        Path.intValue(Path.of("x"), jv, 0),
                        Path.intValue(Path.of("y"), jv, 1)
                )).toList();
        System.out.println(points);

    }
}
