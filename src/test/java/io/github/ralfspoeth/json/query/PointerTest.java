package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.Greyson;
import io.github.ralfspoeth.json.data.Basic;
import io.github.ralfspoeth.json.data.JsonArray;
import io.github.ralfspoeth.json.data.JsonObject;
import io.github.ralfspoeth.json.data.JsonValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.List;

import static io.github.ralfspoeth.json.data.Builder.arrayBuilder;
import static io.github.ralfspoeth.json.data.Builder.objectBuilder;
import static io.github.ralfspoeth.json.query.Pointer.parse;
import static io.github.ralfspoeth.json.query.Pointer.self;
import static org.junit.jupiter.api.Assertions.*;

class PointerTest {


    @Test
    void testNull() {
        // given
        String member = null;
        JsonValue result = null;
        // then
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> self().member(member)),
                () -> assertThrows(NullPointerException.class, () -> self().apply(result))
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
        Pointer a = self().member("a");
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
        Pointer i1 = self().index(1);
        // then
        assertAll(
                () -> assertTrue(i1.apply(ja).isPresent()),
                () -> assertEquals(2, i1.intValue(ja).orElseThrow()),
                () -> assertFalse(i1.apply(jo).isPresent()),
                () -> assertFalse(i1.apply(b).isPresent())
        );
    }

    @Test
    void testRegexFirstMatch() {
        // given
        var jo = objectBuilder()
                .putBasic("a", 1d)
                .putBasic("balance", 2d)
                .build();
        // when
        var ra = self().regex("[aA]");
        var bal = self().regex("bal(ance)?");
        // then single matches behave like a member lookup
        assertAll(
                () -> assertEquals(1d, ra.doubleValue(jo).orElseThrow()),
                () -> assertEquals(2d, bal.doubleValue(jo).orElseThrow())
        );
    }

    @Test
    void testParseRegexSegment() {
        // given an object with both a "bal" key and a "balance" key inside a
        // wrapping member "wallet"
        var jo = objectBuilder()
                .put("wallet", objectBuilder()
                        .putBasic("bal", 1d)
                        .putBasic("balance", 2d))
                .build();
        // when parse() encounters '#regex', it produces the same pointer as
        // chaining .regex(...) by hand
        var fromParse = parse("wallet/#bal(ance)?");
        var fluent = self().member("wallet").regex("bal(ance)?");
        // then both resolve to the lex-smallest matching key
        assertAll(
                () -> assertEquals(1d, fromParse.doubleValue(jo).orElseThrow()),
                () -> assertEquals(1d, fluent.doubleValue(jo).orElseThrow())
        );
    }

    @Test
    void testRegexMultiMatchPicksLexicographicallySmallest() {
        // given an object with both abbreviated and spelled-out forms
        var jo = objectBuilder()
                .putBasic("bal", 1d)
                .putBasic("balance", 2d)
                .build();
        // when the regex matches both keys
        var bal = self().regex("bal(ance)?");
        // then the value of the lexicographically smallest matching key wins
        assertEquals(1d, bal.doubleValue(jo).orElseThrow());
    }

    @Test
    void testFromJsonPointerEmpty() throws IOException {
        var json = """
                {"a": 1}
                """;
        var doc = Greyson.readValue(Reader.of(json)).orElseThrow();
        // empty string addresses the document root
        assertSame(doc, Pointer.fromJsonPointer("").apply(doc).orElseThrow());
    }

    @Test
    void testFromJsonPointerMustStartWithSlash() {
        assertThrows(IllegalArgumentException.class, () -> Pointer.fromJsonPointer("a/b"));
    }

    @Test
    void testFromJsonPointerMemberAndIndexDispatch() {
        // RFC 6901: the same token "0" resolves as a member name in objects
        // and as an array index in arrays.
        var asObject = """
                {"0": "obj-zero"}
                """;
        var asArray = """
                ["arr-zero", "arr-one"]
                """;
        assertAll(
                () -> assertEquals("obj-zero",
                        Pointer.fromJsonPointer("/0")
                                .apply(Greyson.readValue(Reader.of(asObject)).orElseThrow())
                                .flatMap(JsonValue::string)
                                .orElseThrow()),
                () -> assertEquals("arr-zero",
                        Pointer.fromJsonPointer("/0")
                                .apply(Greyson.readValue(Reader.of(asArray)).orElseThrow())
                                .flatMap(JsonValue::string)
                                .orElseThrow())
        );
    }

    @Test
    void testFromJsonPointerEscapes() throws IOException {
        // ~1 -> '/', ~0 -> '~'; the order matters so that "~01" decodes as "~1"
        var json = """
                {"a/b": "slash", "m~n": "tilde", "~1": "literal-tilde-one"}
                """;
        var doc = Greyson.readValue(Reader.of(json)).orElseThrow();
        assertAll(
                () -> assertEquals("slash",
                        Pointer.fromJsonPointer("/a~1b").apply(doc).flatMap(JsonValue::string).orElseThrow()),
                () -> assertEquals("tilde",
                        Pointer.fromJsonPointer("/m~0n").apply(doc).flatMap(JsonValue::string).orElseThrow()),
                // "~01" should decode as the literal "~1", not "/"
                () -> assertEquals("literal-tilde-one",
                        Pointer.fromJsonPointer("/~01").apply(doc).flatMap(JsonValue::string).orElseThrow())
        );
    }

    @Test
    void testFromJsonPointerArrayIndexValidation() throws IOException {
        var arr = """
                [10, 20, 30]
                """;
        var doc = Greyson.readValue(Reader.of(arr)).orElseThrow();
        assertAll(
                () -> assertEquals(20, Pointer.fromJsonPointer("/1").intValue(doc).orElseThrow()),
                // RFC 6901 forbids leading zeros — "01" is not a valid index
                () -> assertTrue(Pointer.fromJsonPointer("/01").apply(doc).isEmpty()),
                // Out-of-bounds is also empty, not an exception
                () -> assertTrue(Pointer.fromJsonPointer("/99").apply(doc).isEmpty()),
                // Negative tokens are not valid RFC 6901 array indices
                () -> assertTrue(Pointer.fromJsonPointer("/-1").apply(doc).isEmpty())
        );
    }

    @Test
    void testIntValue() {
        var jo = objectBuilder()
                .putBasic("n", 42)
                .putBasic("f", 3.7d)
                .putBasic("s", "not a number")
                .build();
        assertAll(
                () -> assertEquals(42, self().member("n").intValue(jo).orElseThrow()),
                // BigDecimal.intValue() truncates the fractional part. Use
                // intValueExact when you want the strict version.
                () -> assertEquals(3, self().member("f").intValue(jo).orElseThrow()),
                // wrong type — strings have no decimal()
                () -> assertTrue(self().member("s").intValue(jo).isEmpty()),
                // missing key
                () -> assertTrue(self().member("missing").intValue(jo).isEmpty())
        );
    }

    @Test
    void testLongValue() {
        var jo = objectBuilder()
                .putBasic("n", 9_876_543_210L)
                .putBasic("s", "not a number")
                .build();
        assertAll(
                () -> assertEquals(9_876_543_210L,
                        self().member("n").longValue(jo).orElseThrow()),
                () -> assertTrue(self().member("s").longValue(jo).isEmpty()),
                () -> assertTrue(self().member("missing").longValue(jo).isEmpty())
        );
    }

    @Test
    void testDoubleValue() {
        var jo = objectBuilder()
                .putBasic("n", 3.14d)
                .putBasic("i", 7)
                .putBasic("s", "not a number")
                .build();
        assertAll(
                () -> assertEquals(3.14d, self().member("n").doubleValue(jo).orElseThrow()),
                // ints widen cleanly through BigDecimal::doubleValue
                () -> assertEquals(7d, self().member("i").doubleValue(jo).orElseThrow()),
                () -> assertTrue(self().member("s").doubleValue(jo).isEmpty()),
                () -> assertTrue(self().member("missing").doubleValue(jo).isEmpty())
        );
    }

    @Test
    void testIntValueExact() {
        var jo = objectBuilder()
                .putBasic("ok", 1000)
                .putBasic("frac", 1.5d)
                .putBasic("big", 10_000_000_000L) // outside int range
                .putBasic("s", "x")
                .build();
        assertAll(
                () -> assertEquals(1000,
                        self().member("ok").intValueExact(jo).orElseThrow()),
                // BigDecimal.intValueExact rejects fractional values
                () -> assertThrows(ArithmeticException.class,
                        () -> self().member("frac").intValueExact(jo)),
                // and rejects values outside the int range
                () -> assertThrows(ArithmeticException.class,
                        () -> self().member("big").intValueExact(jo)),
                // wrong-type and missing keys still return empty without throwing
                () -> assertTrue(self().member("s").intValueExact(jo).isEmpty()),
                () -> assertTrue(self().member("missing").intValueExact(jo).isEmpty())
        );
    }

    @Test
    void testLongValueExact() throws IOException {
        // Build the document via JSON to guarantee a real BigDecimal payload
        // for the out-of-range case (Builder treats numeric primitives via
        // BigDecimal.valueOf which can't accept values larger than Long).
        var doc = Greyson.readValue(Reader.of("""
                {"ok": 1000000000, "frac": 1.5, "huge": 99999999999999999999, "s": "x"}
                """)).orElseThrow();
        assertAll(
                () -> assertEquals(1_000_000_000L,
                        self().member("ok").longValueExact(doc).orElseThrow()),
                () -> assertThrows(ArithmeticException.class,
                        () -> self().member("frac").longValueExact(doc)),
                () -> assertThrows(ArithmeticException.class,
                        () -> self().member("huge").longValueExact(doc)),
                () -> assertTrue(self().member("s").longValueExact(doc).isEmpty()),
                () -> assertTrue(self().member("missing").longValueExact(doc).isEmpty())
        );
    }

    @Test
    void testBooleanValue() {
        var jo = objectBuilder()
                .putBasic("t", true)
                .putBasic("f", false)
                .putBasic("s", "true") // literal string, not a JSON boolean
                .putBasic("n", 1)
                .build();
        assertAll(
                () -> assertTrue(self().member("t").booleanValue(jo).orElseThrow()),
                () -> assertFalse(self().member("f").booleanValue(jo).orElseThrow()),
                // strings and numbers are not coerced; the conversion is type-strict
                () -> assertTrue(self().member("s").booleanValue(jo).isEmpty()),
                () -> assertTrue(self().member("n").booleanValue(jo).isEmpty()),
                () -> assertTrue(self().member("missing").booleanValue(jo).isEmpty())
        );
    }

    @Test
    void testStringValue() {
        var jo = objectBuilder()
                .putBasic("s", "hello")
                .putBasic("n", 42)
                .putBasic("b", true)
                .build();
        assertAll(
                () -> assertEquals("hello",
                        self().member("s").stringValue(jo).orElseThrow()),
                // numbers and booleans are not stringified by the conversion
                () -> assertTrue(self().member("n").stringValue(jo).isEmpty()),
                () -> assertTrue(self().member("b").stringValue(jo).isEmpty()),
                () -> assertTrue(self().member("missing").stringValue(jo).isEmpty())
        );
    }

    @Test
    void testSelectNavigatesThenExpands() {
        // {"data": {"users": [{"id": 1}, {"id": 2}, {"id": 3}]}}
        var doc = objectBuilder()
                .put("data", objectBuilder()
                        .put("users", arrayBuilder()
                                .add(objectBuilder().putBasic("id", 1))
                                .add(objectBuilder().putBasic("id", 2))
                                .add(objectBuilder().putBasic("id", 3))))
                .build();
        // Navigate to the users array, then stream every element of it.
        var users = parse("data/users").select(Selector.all());
        var ids = users.apply(doc)
                .flatMap(v -> v.get("id").stream())
                .flatMap(v -> v.decimal().stream())
                .map(BigDecimal::intValue)
                .toList();
        assertEquals(List.of(1, 2, 3), ids);
    }

    @Test
    void testSelectEmptyWhenPointerDoesNotResolve() {
        // The pointer fails to resolve "missing", so the selector never runs.
        var doc = objectBuilder().putBasic("a", 1).build();
        var fn = self().member("missing").select(Selector.all());
        assertEquals(0L, fn.apply(doc).count());
    }

    @Test
    void testSelectWithSelfPointerEqualsSelectorApply() {
        // self() navigates nowhere, so self().select(s) ≡ s.apply.
        var arr = arrayBuilder().addBasic(1).addBasic(2).addBasic(3).build();
        var direct = Selector.all().apply(arr).toList();
        var viaPointer = self().select(Selector.all()).apply(arr).toList();
        assertEquals(direct, viaPointer);
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
        Pointer i0 = self().index(0);
        Pointer i1 = self().index(1);
        Pointer ma = self().member("a");
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
        var b = arrayBuilder();
        var ptr = self();
        var ab = b;
        for (int i = 0; i < 499; i++) {
            var tmp = arrayBuilder();
            ab.add(tmp);
            ab = tmp;
            ptr = ptr.resolve(self().index(0));
        }
        // when
        var ja = b.build();

        // then
        System.out.println(ja);
        System.out.println(ja.depth());
        System.out.println(ja.nodes());
        System.out.println(ptr.apply(ja).orElseThrow());
    }

    @Test
    void testLargeObject() {
        // given
        var b = objectBuilder();
        var ptr = self();
        var ob = b;
        for (int i = 0; i < 499; i++) {
            var tmp = objectBuilder();
            ob.put("a", tmp);
            ob = tmp;
            ptr = ptr.resolve(self().member("a"));
        }
        // when
        var jo = b.build();
        // then
        System.out.println(jo);
        System.out.println(ptr.apply(jo).orElseThrow());
    }
}
