package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.Basic;
import io.github.ralfspoeth.json.Element;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathTest {

    @Test
    void ofEmpty() {
        assertThrows(NullPointerException.class, () -> Path.of(null));
    }

    @Test
    void ofSingle() {
        var five = Basic.of(5);
        var singleElem = Element.objectBuilder().named("one", five).build();
        assertAll(
                () -> assertEquals(Path.of("one"), Path.of("one")),
                () -> assertTrue(Path.of("one").evaluate(singleElem).allMatch(five::equals))
        );
    }

    @Test
    void ofRange() {
        var five = Basic.of(5);
        var singleElemArray = Element.arrayBuilder().item(five).build();
        var multiElemArray = Element.arrayBuilder().item(five).item(five).item(five).build();
        assertAll(
                () -> assertEquals(Path.of("[0..-5]"), Path.of("[0..-1]")),
                () -> assertTrue(Path.of("[0..-1]").evaluate(singleElemArray).allMatch(five::equals)),
                () -> assertTrue(Path.of("[0..-1]").evaluate(multiElemArray).allMatch(five::equals)),
                () -> assertTrue(Path.of("[0..1]").evaluate(multiElemArray).allMatch(five::equals)),
                () -> assertTrue(Path.of("[0..2]").evaluate(multiElemArray).allMatch(five::equals)),
                () -> assertEquals(2, Path.of("[0..2]").evaluate(multiElemArray).count()),
                () -> assertTrue(Path.of("[0..5]").evaluate(multiElemArray).allMatch(five::equals)),
                () -> assertEquals(3, Path.of("[0..5]").evaluate(multiElemArray).count())

        );
    }

    @Test
    void ofNameRangeRegex() {
        var root = Element.objectBuilder()
                .named("one", Element.arrayBuilder().item(Element.objectBuilder().named("two", Basic.of(5))).build())
                .build();
        var path = Path.of("one/[0..1]/#t.*o");
        Assertions.assertEquals(Basic.of(5), path.evaluate(root).findFirst().orElseThrow());
    }

    @Test
    void ofRegex() {
        var path = Path.of("#o.*e");
        var five = Basic.of(5);
        var singleElem = Element.objectBuilder().named("oe", five).build();
        Assertions.assertEquals(five, path.evaluate(singleElem).findFirst().orElseThrow());
    }

}