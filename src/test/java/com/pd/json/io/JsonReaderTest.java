package com.pd.json.io;

import com.pd.json.data.*;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JsonReaderTest {

    @Test
    public void testEmptyObject() throws IOException {
        var source = "{}";
        try (var rdr = new StringReader(source);
             var parser = new JsonReader(rdr)) {
            var result = parser.readElement();
            assertAll(
                    () -> assertNotNull(result),
                    () -> assertTrue(result instanceof JsonObject o && o.members().isEmpty())
            );
        }
    }

    @Test
    public void testEmptyArray() throws IOException {
        var source = "[]";
        try (var rdr = new StringReader(source);
             var parser = new JsonReader(rdr)) {
            var result = parser.readElement();
            assertAll(
                    () -> assertNotNull(result),
                    () -> assertTrue(result instanceof JsonArray a && a.elements().isEmpty())
            );
        }
    }

    @Test
    public void testSingleElems() {
        var sources = List.of("1", "true", "null", "false", "'str'");
        sources.forEach(source -> {
            try (var parser = new JsonReader(new StringReader(source))) {
                var v = parser.readElement();
                assertAll(() -> assertTrue(v instanceof JsonValue));
            } catch (IOException ioex) {
                assertNull(ioex);
            }
        });
    }

    @Test
    public void testSingleElemArray() {
        var sources = List.of("[1]", "[null]", "[false]", "[true]", "['str']");
        sources.forEach(source -> {
            try (var rdr = new StringReader(source);
                 var parser = new JsonReader(rdr)) {
                var result = parser.readElement();
                assertAll(
                        () -> assertNotNull(result),
                        () -> assertTrue(result instanceof JsonArray a && a.elements().size() == 1)
                );
            } catch (IOException ioex) {
                assertNull(ioex);
            }
        });
    }

    @Test
    public void testSingleMemberObject() throws IOException {
        var source = "{'n':5}";
        try (var r = new JsonReader(new StringReader(source))) {
            var o = r.readElement();
            assertAll(() -> assertTrue(o instanceof JsonObject),
                    () -> assertEquals(1, ((JsonObject) o).members().size()),
                    () -> assertEquals(new JsonObject(Map.of("n", new JsonNumber(5d))), o)
            );
        }
    }

    @Test
    public void testDualMemberObject() throws Exception {
        var source = "{'n':5, 'm': 7}";
        try(var r = new JsonReader(new StringReader(source))) {
            r.readElement();
        }
    }

    @Test
    public void testArrayOfValues() throws IOException {
        String source = "[5, 6, 7, false, null, true, 'str']";// "[{'n':5}, {'m':6}]";
        try (var p = new JsonReader(new StringReader(source))) {
            var a = p.readElement();
            assertAll(
                    () -> assertTrue(a instanceof JsonArray),
                    () -> assertEquals(7, ((JsonArray) a).elements().size()),
                    () -> assertTrue(((JsonArray) a).elements().contains(new JsonString("str")))
            );
        }
    }

    @Test
    public void testArrayOfObject() throws IOException {
        String source = "[{'n':55}]";
        try (var p = new JsonReader(new StringReader(source))) {
            var a = p.readElement();
            assertAll(
                    () -> assertTrue(a instanceof JsonArray),
                    () -> assertEquals(1, ((JsonArray) a).elements().size()),
                    () -> assertTrue(((JsonArray) a).elements().contains(
                            new JsonObject(Map.of("n", new JsonNumber(55)))
                    ))
            );
        }
    }   @Test
    public void testArrayOfTwoObjects() throws IOException {
        String source = "[{'n':55}, {'m':7}]";
        try (var p = new JsonReader(new StringReader(source))) {
            var a = p.readElement();
            assertAll(
                    () -> assertTrue(a instanceof JsonArray),
                    () -> assertEquals(2, ((JsonArray) a).elements().size()),
                    () -> assertTrue(((JsonArray) a).elements().contains(
                            new JsonObject(Map.of("m", new JsonNumber(7)))
                    ))
            );
        }
    }

    @Test
    public void testNestedObjectDepth1() throws IOException {
        String source = "{'a':{'b':[]}}";
        try(var p = new JsonReader(new StringReader(source))) {
            var e = p.readElement();
            if(e instanceof JsonObject o0) {
                assertAll(
                        () -> assertEquals(1, o0.members().size()),
                        () -> assertTrue(o0.members().containsKey("a"))
                );
                if(o0.members().get("a") instanceof JsonObject o1) {
                    assertAll(
                            () -> assertEquals(1, o1.members().size()),
                            () -> assertTrue(o1.members().containsKey("b")),
                            () -> assertEquals(new JsonArray(List.of()), o1.members().get("b"))
                    );
                }
                else {
                    fail("not a JsonObject");
                }
            }
            else {
                fail("not a JsonObject");
            }
        }
    }

    @Test
    public void testParseLarge() throws Exception {
        try(var src = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/large-file.json"),
                StandardCharsets.UTF_8
        )); var rdr = new JsonReader(src))
        {
            try {
                var result = rdr.readElement();
                assertNotNull(result);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}