package io.github.ralfspoeth.json.io;

import io.github.ralfspoeth.json.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

class JsonReaderTest {

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
                    () -> Assertions.assertEquals(new JsonObject(Map.of("n", new JsonNumber(5d))), o)
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
            while(jdr.hasNext()) {
                System.out.println(jdr.next());
            }
        }
    }

    private Reader largeFile() {
        return new BufferedReader(new InputStreamReader(
                requireNonNull(getClass().getResourceAsStream("/large-file.json")),
                StandardCharsets.UTF_8
        ));
    }
}