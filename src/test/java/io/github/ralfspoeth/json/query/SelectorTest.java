package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.data.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.data.Builder.arrayBuilder;
import static io.github.ralfspoeth.json.data.Builder.objectBuilder;
import static io.github.ralfspoeth.json.query.Selector.*;
import static java.util.Collections.disjoint;
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

    @Test
    void testRange() {
        // given
        var ja = arrayBuilder().addBasic(1).addBasic(2).addBasic(3).build();
        // when
        var rg12 = Selector.range(1, 2);
        var rg03 = Selector.range(0, 3);
        var rg0neg = Selector.range(0, -1);
        // then
        assertAll(
                ()->assertEquals(List.of(Basic.of(2)), Stream.of(ja).flatMap(rg12).toList()),
                ()->assertEquals(List.of(Basic.of(1), Basic.of(2), Basic.of(3)), Stream.of(ja).flatMap(rg03).toList()),
                ()->assertEquals(List.of(Basic.of(1), Basic.of(2), Basic.of(3)), Stream.of(ja).flatMap(rg0neg).toList())
        );
    }

    @Test
    void testRegex() {
        // given
        var jo = objectBuilder().putBasic("a1", 1).putBasic("a2", 2).putBasic("a3", 3).build();
        // when
        var aDigit = Selector.regex("a[0-9]+");
        var a12 = Selector.regex("a[1-2]");
        var a3 = Selector.regex("a3");
        var l123 = List.of(Basic.of(1), Basic.of(2), Basic.of(3 ));
        var l12 = List.of(Basic.of(1), Basic.of(2));
        var l3 = List.of(Basic.of(3));
        // then
        assertAll(
                () -> assertFalse(disjoint(l123, Stream.of(jo).flatMap(aDigit).toList())),
                () -> assertFalse(disjoint(l12, Stream.of(jo).flatMap(a12).toList())),
                () -> assertFalse(disjoint(l3, Stream.of(jo).flatMap(a3).toList()))
        );
    }
}
