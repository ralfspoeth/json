package com.github.ralfspoeth.json.query;

import com.github.ralfspoeth.json.*;
import org.junit.jupiter.api.Test;

import static com.github.ralfspoeth.json.JsonElement.arrayBuilder;
import static com.github.ralfspoeth.json.JsonElement.objectBuilder;
import static org.junit.jupiter.api.Assertions.*;

class PathTest {

    @Test
    void ofEmpty() {
        assertThrows(NullPointerException.class, ()->Path.of(null));
    }

    @Test
    void ofSingle() {
        var five = JsonValue.of(5);
        var singleElem = objectBuilder().named("one", five).build();
        assertAll(
                () -> assertEquals(Path.of("one"), Path.of("one")),
                () -> assertTrue(Path.of("one").evaluate(singleElem).allMatch(five::equals))
        );
    }

    @Test
    void ofRange() {
        var five = JsonValue.of(5);
        var singleElem = arrayBuilder().item(five).build();
        assertAll(
                () -> assertEquals(Path.of("[0..1]"), Path.of("[0..1]")),
                () -> assertTrue(Path.of("[0..-1]").evaluate(singleElem).allMatch(five::equals))
        );
    }

    @Test
    void ofNameRangeRegex() {
        var root = objectBuilder()
                .named("one", arrayBuilder().item(objectBuilder().named("two", JsonValue.of(5))).build())
                .build();
        var path = Path.of("one/[0..1]/#t.*o");
        assertEquals(JsonValue.of(5), path.evaluate(root).findFirst().orElseThrow());
    }

    @Test
    void ofRegex() {
        var path = Path.of("#o.*e");
        var five = JsonValue.of(5);
        var singleElem = objectBuilder().named("oe", five).build();
        assertEquals(five, path.evaluate(singleElem).findFirst().orElseThrow());
    }

}