package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.Greyson;
import io.github.ralfspoeth.json.data.Basic;
import io.github.ralfspoeth.json.data.JsonArray;
import io.github.ralfspoeth.json.data.JsonObject;
import io.github.ralfspoeth.json.data.JsonValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;

import static io.github.ralfspoeth.json.data.Builder.arrayBuilder;
import static io.github.ralfspoeth.json.data.Builder.objectBuilder;
import static io.github.ralfspoeth.json.query.Pointer.parse;
import static org.junit.jupiter.api.Assertions.*;

class PointerTest {


    @Test
    void testNull() {
        // given
        String member = null;
        JsonValue result = null;
        // then
        assertAll(
                () -> assertThrows(NullPointerException.class, ()->Pointer.self().member(member)),
                () -> assertThrows(NullPointerException.class, ()->Pointer.self().apply(result))
        );
    }

    @Test
    void ofSingle() {
        // given
        var five = Basic.of(5);
        var singleElem = objectBuilder().put("one", five).build();
        // then
        assertAll(
                () -> assertEquals(five, parse("one").apply(singleElem).orElseThrow())
        );
    }

    @Test
    void testMember() {
        // given
        var ja = arrayBuilder().build();
        var jo = objectBuilder().putBasic("a", 1).putBasic("b", 2).build();
        var b = Basic.of(3);
        // when
        Pointer a = Pointer.self().member("a");
        // then
        assertAll(
                () -> assertFalse(a.apply(ja).isPresent()),
                () -> assertFalse(a.apply(b).isPresent()),
                () -> assertEquals(1, a.intValue(jo).orElseThrow())
        );
    }

    @Test
    void testIndex() {
        // given
        var ja = arrayBuilder().addBasic(1).addBasic(2).build();
        var jo = objectBuilder().putBasic("a", 1).putBasic("b", 2).build();
        var b = Basic.of(3);
        // when
        Pointer i1 = Pointer.self().index(1);
        // then
        assertAll(
                () -> assertTrue(i1.apply(ja).isPresent()),
                () -> assertEquals(2, i1.intValue(ja).orElseThrow()),
                () -> assertFalse(i1.apply(jo).isPresent()),
                () -> assertFalse(i1.apply(b).isPresent())
        );
    }

    @Test
    void testResolve() throws IOException {
        // given
        var json = """
                [
                    {"a": [1, 2, 3]},
                    [{"a":4}],
                    null
                ]
                """;
        // when
        Pointer i0 = Pointer.self().index(0);
        Pointer i1 = Pointer.self().index(1);
        Pointer ma = Pointer.self().member("a");
        Pointer i0ma = i0.resolve(ma);
        Pointer i0mai1 = i0ma.resolve(i1);
        // then
        var opt = Greyson.readValue(Reader.of(json));
        var val = opt.orElseThrow();
        assertAll(
                () -> assertInstanceOf(JsonObject.class, opt.flatMap(i0).orElseThrow()),
                () -> assertInstanceOf(JsonArray.class, opt.flatMap(i1).orElseThrow()),
                () -> assertInstanceOf(JsonArray.class, opt.flatMap(i0ma).orElseThrow()),
                () -> assertEquals(2, i0mai1.intValue(val).orElseThrow())
        );
    }

    @Test
    void testLargeArray() {
        // given
        var bldr = arrayBuilder();
        var ptr =  Pointer.self();
        var ab = bldr;
        for(int i=0;i<499;i++) {
            var tmp = arrayBuilder();
            ab.add(tmp);
            ab = tmp;
            ptr = ptr.resolve(Pointer.self().index(0));
        }
        // when
        var ja = bldr.build();

        // then
        System.out.println(ja);
        System.out.println(ja.depth());
        System.out.println(ja.nodes());
        System.out.println(ptr.apply(ja).orElseThrow());
    }

    @Test
    void testLargeObject() {
        // given
        var bldr = objectBuilder();
        var ptr =  Pointer.self();
        var ob =  bldr;
        for(int i=0;i<499;i++) {
            var tmp = objectBuilder();
            ob.put("a", tmp);
            ob = tmp;
            ptr = ptr.resolve(Pointer.self().member("a"));
        }
        // when
        var jo = bldr.build();
        // then
        System.out.println(jo);
        System.out.println(ptr.apply(jo).orElseThrow());
    }
}
