package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.data.Basic;
import io.github.ralfspoeth.json.data.JsonArray;
import io.github.ralfspoeth.json.data.JsonNull;
import io.github.ralfspoeth.json.data.JsonObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.data.Builder.arrayBuilder;
import static io.github.ralfspoeth.json.data.Builder.objectBuilder;
import static io.github.ralfspoeth.json.query.Pointer.self;
import static io.github.ralfspoeth.json.query.Selector.all;
import static io.github.ralfspoeth.json.query.Selector.regex;
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
                () -> assertEquals(List.of(Basic.of(2)), Stream.of(ja).flatMap(rg12).toList()),
                () -> assertEquals(List.of(Basic.of(1), Basic.of(2), Basic.of(3)), Stream.of(ja).flatMap(rg03).toList()),
                () -> assertEquals(List.of(Basic.of(1), Basic.of(2), Basic.of(3)), Stream.of(ja).flatMap(rg0neg).toList())
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
        var l123 = List.of(Basic.of(1), Basic.of(2), Basic.of(3));
        var l12 = List.of(Basic.of(1), Basic.of(2));
        var l3 = List.of(Basic.of(3));
        // then
        assertAll(
                () -> assertFalse(disjoint(l123, Stream.of(jo).flatMap(aDigit).toList())),
                () -> assertFalse(disjoint(l12, Stream.of(jo).flatMap(a12).toList())),
                () -> assertFalse(disjoint(l3, Stream.of(jo).flatMap(a3).toList()))
        );
    }

    @Test
    void testPointDropsUnresolved() {
        // given a heterogeneous array — some elements have a "name", some don't
        var arr = arrayBuilder()
                .add(objectBuilder().putBasic("id", 1).putBasic("name", "Ada"))
                .add(objectBuilder().putBasic("id", 2))                          // no name
                .add(objectBuilder().putBasic("id", 3).putBasic("name", "Bea"))
                .build();
        // when each element is narrowed to "name"
        var names = all().point(self().member("name"));
        // then unresolved elements drop silently — Optional::stream is empty for empty optionals
        var result = Stream.of(arr).flatMap(names)
                .flatMap(v -> v.string().stream())
                .toList();
        assertEquals(List.of("Ada", "Bea"), result);
    }

    @Test
    void testPointWhenSelectorYieldsNothing() {
        // an out-of-range slice produces no elements regardless of pointer
        var arr = arrayBuilder().addBasic(1).addBasic(2).build();
        var fn = Selector.range(5, 10).point(self());
        assertEquals(0L, Stream.of(arr).flatMap(fn).count());
    }

    @Test
    void testPointWithSelfPointerIsIdentity() {
        // .point(self()) should leave the selector's stream untouched; the
        // pointer is the one that resolves every value to itself
        var arr = arrayBuilder().addBasic(1).addBasic(2).addBasic(3).build();
        var direct = Stream.of(arr).flatMap(all()).toList();
        var viaPoint = Stream.of(arr).flatMap(all().point(self())).toList();
        assertEquals(direct, viaPoint);
    }

    @Test
    void testMeAndPointer() {
        // given [{"a": 1}, {"a":2, "b": 2}, {"d": 4}, {"a":4}]
        var ja = arrayBuilder()
                .add(objectBuilder().putBasic("a", 1))
                .add(objectBuilder().putBasic("a", 2).putBasic("b", 2))
                .add(objectBuilder().putBasic("d", 4))
                .add(objectBuilder().putBasic("a", 3).putBasic("c", 4))
                .build();
        // when
        var as = Stream.of(ja)
                .flatMap(Selector
                        .all() // a singleton or every array element
                        .point(self().member("a")) // member "a"
                )
                .flatMap(a -> a.decimal().map(BigDecimal::intValue).stream())
                .toList();
        // then
        assertEquals(List.of(1, 2, 3), as);
    }


}
