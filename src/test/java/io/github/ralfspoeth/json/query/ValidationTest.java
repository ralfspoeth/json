package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.Aggregate.arrayBuilder;
import static io.github.ralfspoeth.json.JsonBoolean.FALSE;
import static io.github.ralfspoeth.json.JsonBoolean.TRUE;
import static io.github.ralfspoeth.json.JsonNull.INSTANCE;
import static io.github.ralfspoeth.json.query.Validation.*;
import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {

    @Test
    void testTypes() {
        assertAll(
                () -> assertTrue(is(JsonString.class).test(Basic.of("hello"))),
                () -> assertFalse(is(JsonString.class).test(Basic.of(false))),
                () -> assertFalse(is(JsonString.class).test(Basic.of(true))),
                () -> assertFalse(is(JsonString.class).test(Basic.of(null))),
                () -> assertFalse(is(JsonString.class).test(Basic.of(5))),
                // JsonBoolean
                () -> assertTrue(is(JsonBoolean.class).test(TRUE)),
                () -> assertTrue(is(JsonBoolean.class).test(FALSE)),
                () -> assertFalse(is(JsonBoolean.class).test(INSTANCE)),
                () -> assertFalse(is(JsonBoolean.class).test(Basic.of(5))),
                () -> assertFalse(is(JsonBoolean.class).test(Basic.of("ehllo"))),
                // JsonNumber
                () -> assertTrue(is(JsonNumber.class).test(Basic.of(5))),
                () -> assertTrue(is(JsonNumber.class).test(Basic.of(0))),
                () -> assertFalse(is(JsonNumber.class).test(INSTANCE)),
                () -> assertFalse(is(JsonNumber.class).test(TRUE)),
                () -> assertFalse(is(JsonNumber.class).test(Basic.of("ehllo")))
        );
    }

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
        var obj = new JsonObject(Map.of("a", Basic.of(5), "b", TRUE, "c", Basic.of("zeh")));
        // when
        var struc1 = matches(Map.of(
                "a", is(JsonNumber.class),
                "b", is(JsonBoolean.class),
                "c", regex("[a-z]+")
        )); // all keys included
        var struc2 = matches(Map.of("a", Basic.of(5))); // incomplete
        var struc3 = matches(Map.of(
                "a", is(JsonString.class), // not a string, must fail
                "b", FALSE // not false but true
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
        Map<String, JsonValue> data = Map.of("a", Basic.of(5), "b", TRUE);
        var obj = new JsonObject(data);
        // when
        var struc1 = required(Map.of("a", is(JsonNumber.class), "b", is(JsonBoolean.class))); // keys and types
        var struc2 = required(Map.of("a", Basic.of(5), "b", TRUE)); // exact map
        var struc3 = required(Map.of("c", INSTANCE)); // key c missing
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
    void superSimple() {
        var array = arrayBuilder().basic(true).build();
        var struc = matchesTypesOf(array);
        System.out.println(TRUE.getDeclaringClass());
        assertTrue(struc.test(new JsonArray(List.of(FALSE))));
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
                TRUE,
                INSTANCE,
                Basic.of(5),
                Basic.of("hello"),
                new JsonObject(Map.of("a", FALSE, "b", Basic.of(7))),
                new JsonArray(List.of(Basic.of(9)))
        ));
        // when
        var arrayTypeStructure = matchesTypesOf(array);
        // then
        assertAll(
                () -> assertTrue(arrayTypeStructure.test(new JsonArray(List.of(
                        FALSE, INSTANCE, Basic.of(-5), Basic.of("ola"),
                        new JsonObject(Map.of("a", TRUE, "b", Basic.of(-7))),
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
                Stream.of(INSTANCE, TRUE)
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
                () -> assertTrue(any(INSTANCE).test(array)), //... a null
                () -> assertFalse(any(string).test(array)) // ... but no strings
        );
    }

    @Test
    void testMatchesOrThrow() {
        // given
        var value = TRUE;
        // then
        assertAll(
                () -> assertThrows(ValidationException.class, () -> matchesOrThrow(FALSE).test(value)),
                () -> assertDoesNotThrow(() -> matchesOrThrow(TRUE).test(value))
        );
    }
}
