package io.github.ralfspoeth.json.io;

import io.github.ralfspoeth.json.*;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

class JsonReaderTest {

    record Result(Element elem, Exception ex) {}

    Result parse(String text) {
        try(var jr = new JsonReader(new StringReader(text))) {
            return new Result(jr.readElement(), null);
        } catch (Exception e) {
            return new Result(null, e);
        }
    }

    @Test
    void testMinus1twodots() {
        var result = parse("[-1.0.]");
        assertAll(
                ()->assertNull(result.elem),
                ()->assertInstanceOf(JsonParseException.class, result.ex)
        );
    }

    @Test
    void testCommaAfterArrayClose() {
        var src = "[1],";
        var result = parse(src);
        assertAll(
                () -> assertNull(result.elem),
                () -> assertInstanceOf(JsonParseException.class, result.ex)
        );
    }
    @Test
    void testCommaAfterObjectClose() throws IOException {
        var src = "[],";
        try(var jr = new JsonReader(new StringReader(src))) {
            assertAll(
                    () -> assertTrue(jr.hasNext()),
                    () -> assertEquals(new JsonArray(List.of()), jr.next()),
                    () -> assertThrows(JsonParseException.class, jr::hasNext)
            );
        }
    }

    @Test
    void test1TrueNoComma() {
        var src = "[1 true]";
        var result = parse(src);
        assertAll(
                () -> assertNull(result.elem),
                () -> assertInstanceOf(JsonParseException.class, result.ex)
        );
    }

    @Test
    void testTwoPairNoComma() {
        var src = """
                {"a":5
                 "b":6}"""; // comma missing
        Element result = null;
        Exception ex = null;
        try(var rdr = new JsonReader(new StringReader(src))) {
            result = rdr.readElement();
        } catch (Exception e) {
            ex = e;
        }
        final var tmpResult = result;
        final var tmpEx = ex;
        assertAll(
                () -> assertNull(tmpResult),
                () -> assertInstanceOf(JsonParseException.class, tmpEx)
        );
    }

    @Test
    void testEmptyObject() throws IOException {
        var source = "{}";
        try (var rdr = new StringReader(source);
             var parser = new JsonReader(rdr)) {
            var result = parser.readElement();
            assertAll(
                    () -> assertNotNull(result),
                    () -> assertTrue(result instanceof JsonObject(var members) && members.isEmpty())
            );
        }
    }

    @Test
    void testEmptyArray() throws IOException {
        var source = "[]";
        try (var rdr = new StringReader(source);
             var parser = new JsonReader(rdr)) {
            var result = parser.readElement();
            assertAll(
                    () -> assertNotNull(result),
                    () -> assertTrue(result instanceof JsonArray(var elements) && elements.isEmpty())
            );
        }
    }

    @Test
    void testSingleElems() {
        var sources = List.of("1", "true", "null", "false", "\"str\"");
        sources.forEach(source -> {
            try (var parser = new JsonReader(new StringReader(source))) {
                var v = parser.readElement();
                assertAll(() -> assertInstanceOf(Basic.class, v));
            } catch (IOException ioex) {
                assertNull(ioex);
            }
        });
    }

    @Test
    void testSingleElemArray() {
        var sources = List.of("[1]", "[null]", "[false]", "[true]", "[\"str\"]");
        sources.forEach(source -> {
            try (var rdr = new StringReader(source);
                 var parser = new JsonReader(rdr)) {
                var result = parser.readElement();
                assertAll(
                        () -> assertNotNull(result),
                        () -> assertTrue(result instanceof JsonArray(var elements) && elements.size() == 1)
                );
            } catch (IOException ioex) {
                assertNull(ioex);
            }
        });
    }

    @Test
    void testSingleMemberObject() throws IOException {
        var source = "{\"n\":5}";
        try (var r = new JsonReader(new StringReader(source))) {
            var o = r.readElement();
            assertAll(() -> assertInstanceOf(JsonObject.class, o),
                    () -> assertEquals(1, o instanceof JsonObject(var members) ? members.size() : -1),
                    () -> assertEquals(new JsonObject(Map.of("n", new JsonNumber(5d))), o)
            );
        }
    }

    @Test
    void testDualMemberObject() throws Exception {
        var source = "{\"n\":5, \"m\": 7}";
        try (var r = new JsonReader(new StringReader(source))) {
            r.readElement();
        }
    }

    @Test
    void testArrayOfValues() throws IOException {
        String source = "[5, 6, 7, false, null, true, \"str\"]";// "[{\"n\":5}, {\"m\":6}]";
        try (var p = new JsonReader(new StringReader(source))) {
            var a = p.readElement();
            assertAll(
                    () -> assertInstanceOf(JsonArray.class, a),
                    () -> assertEquals(7, a instanceof JsonArray(var elements) ? elements.size() : -1),
                    () -> assertTrue(a instanceof JsonArray(var elements) && elements.contains(new JsonString("str")))
            );
        }
    }

    @Test
    void testArrayOfObject() throws IOException {
        String source = "[{\"n\":55}]";
        try (var p = new JsonReader(new StringReader(source))) {
            var a = p.readElement();
            assertAll(
                    () -> assertInstanceOf(JsonArray.class, a),
                    () -> assertEquals(1, ((JsonArray) a).elements().size()),
                    () -> assertTrue(((JsonArray) a).elements().contains(
                            new JsonObject(Map.of("n", new JsonNumber(55)))
                    ))
            );
        }
    }

    @Test
    void testArrayOfTwoObjects() throws IOException {
        String source = "[{\"n\":55}, {\"m\":7}]";
        try (var p = new JsonReader(new StringReader(source))) {
            var a = p.readElement();
            assertAll(
                    () -> assertInstanceOf(JsonArray.class, a),
                    () -> assertEquals(2, ((JsonArray) a).elements().size()),
                    () -> assertTrue(((JsonArray) a).elements().contains(
                            new JsonObject(Map.of("m", new JsonNumber(7)))
                    ))
            );
        }
    }

    @Test
    void testNestedObjectDepth1() throws IOException {
        String source = "{\"a\":{\"b\":[]}}";
        try (var p = new JsonReader(new StringReader(source))) {
            var e = p.readElement();
            if (e instanceof JsonObject(var members)) {
                assertAll(
                        () -> assertEquals(1, members.size()),
                        () -> assertTrue(members.containsKey("a"))
                );
                if (members.get("a") instanceof JsonObject(var membersa)) {
                    assertAll(
                            () -> assertEquals(1, membersa.size()),
                            () -> assertTrue(membersa.containsKey("b")),
                            () -> assertEquals(new JsonArray(List.of()), membersa.get("b"))
                    );
                } else {
                    fail("not a JsonObject");
                }
            } else {
                fail("not a JsonObject");
            }
        }
    }

    @Test
    void testParseString(){
        assertEquals(JsonNull.INSTANCE, JsonReader.readElement("null"));
    }

    @Test
    void testParseLarge() throws Exception {
        try (var src = largeFile(); var rdr = new JsonReader(src)) {
            var result = rdr.readElement();
            assertNotNull(result);
        }
    }

    @Test
    void testIterator() throws IOException {
        try (var src = new StringReader("1 2 3"); var jdr = new JsonReader(src)) {
            var l = StreamSupport.stream(Spliterators.spliteratorUnknownSize(jdr, Spliterator.IMMUTABLE), false)
                    .toList();
            assertArrayEquals(new Object[]{
                    new JsonNumber(1),
                            new JsonNumber(2),
                            new JsonNumber(3)
                    }, l.toArray()
            );
        }
    }

    private Reader largeFile() {
        return new BufferedReader(new InputStreamReader(
                requireNonNull(getClass().getResourceAsStream("/large-file.json")),
                StandardCharsets.UTF_8
        ));
    }
}