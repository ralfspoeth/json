package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.Aggregate.objectBuilder;
import static org.junit.jupiter.api.Assertions.*;

class PathTest {

    @Test
    void ofEmpty() {
        assertThrows(NullPointerException.class, () -> Path.of(null));
    }

    @Test
    void ofSingle() {
        var five = Basic.of(5);
        var singleElem = objectBuilder().named("one", five).build();
        assertAll(
                () -> assertEquals(Path.of("one"), Path.of("one")),
                () -> assertTrue(Path.of("one").apply(singleElem).allMatch(five::equals))
        );
    }

    @Test
    void ofRange() {
        var five = Basic.of(5);
        var singleElemArray = Aggregate.arrayBuilder().item(five).build();
        var multiElemArray = Aggregate.arrayBuilder().item(five).item(five).item(five).build();
        assertAll(
                () -> assertEquals(Path.of("[0..-5]"), Path.of("[0..-1]")),
                () -> assertTrue(Path.of("[0..-1]").apply(singleElemArray).allMatch(five::equals)),
                () -> assertTrue(Path.of("[0..-1]").apply(multiElemArray).allMatch(five::equals)),
                () -> assertTrue(Path.of("[0..1]").apply(multiElemArray).allMatch(five::equals)),
                () -> assertTrue(Path.of("[0..2]").apply(multiElemArray).allMatch(five::equals)),
                () -> assertEquals(2, Path.of("[0..2]").apply(multiElemArray).count()),
                () -> assertTrue(Path.of("[0..5]").apply(multiElemArray).allMatch(five::equals)),
                () -> assertEquals(3, Path.of("[0..5]").apply(multiElemArray).count())

        );
    }

    @Test
    void ofNameRangeRegex() {
        var root = objectBuilder()
                .named("one", new JsonArray(List.of(
                        new JsonObject(Map.of("two", Basic.of(5)))
                )))
                .build();
        var path = Path.of("one/[0..1]/#t.*o");
        Assertions.assertEquals(Basic.of(5), path.apply(root).findFirst().orElseThrow());
    }

    @Test
    void ofRegex() {
        var path = Path.of("#o.*e");
        var five = Basic.of(5);
        var singleElem = objectBuilder().named("oe", five).build();
        Assertions.assertEquals(five, path.apply(singleElem).findFirst().orElseThrow());
    }

    @Test
    void testFlatMap() {
        var array = JsonArray.ofArray(new int[]{1, 2, 3, 4});
        var path = Path.of("[0..4]");
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
                .flatMap(Path.of("a"))
                .flatMap(Path.of("b"))
                .flatMap(Path.of("c"))
                .toList();
        assertAll(
                () -> assertEquals(new JsonString("Zeh"), l.getFirst()),
                () -> assertEquals(new JsonString("Zeh"), l.getLast())
        );

        var m = Stream.of(obj)
                .flatMap(Path.of("a/b/c"))
                .toList();
        assertAll(
                () -> assertEquals(new JsonString("Zeh"), m.getFirst()),
                () -> assertEquals(new JsonString("Zeh"), m.getLast())
        );
    }

}