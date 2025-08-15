package io.github.ralfspoeth.json.io;

import io.github.ralfspoeth.json.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonWriterTest {

    @Test
    void test1() throws Exception {
        var orig = Builder.objectBuilder()
                .put("a", Basic.of(5))
                .basic("bb", 6d)
                .basic("fuck", null)
                .put("arr", Builder.arrayBuilder()
                        .add(Basic.of(null))
                        .add(Basic.of(7.5))
                        .add(Builder.objectBuilder().build())
                        .add(Builder.objectBuilder().basic("a", 5d).build())
                        .build())
                .build();

        try (var pw = new StringWriter(); var wrt = new JsonWriter(new PrintWriter(pw))) {
            wrt.write(orig);
            try(var sr = new StringReader(pw.toString()); var rdr = new JsonReader(sr)) {
                var result = rdr.readElement();
                assertEquals(orig, result);
            }
        }
    }

    @Test
    void testStrings() {
        var src = """
                Hello "World",\tthis
                is / very cool
                """ + (char)0x01;
        var json = "\"Hello \\\"World\\\",\\tthis\\nis \\/ very cool\\n\\u0001\"";
        assertEquals(json, new JsonString(src).json());
    }

    @Test
    void testBasics() throws IOException {
        try(var w = new StringWriter(); var jw = new JsonWriter(w)) {
            int len = 0;
            jw.write(Basic.of(1.2));
            assertEquals("1.2", w.getBuffer().substring(len, len+=3));
            jw.write(JsonNull.INSTANCE);
            assertEquals("null", w.getBuffer().substring(len, len+=4));
            jw.write(JsonBoolean.TRUE);
            assertEquals("true", w.getBuffer().substring(len, len+=4));
            jw.write(JsonBoolean.FALSE);
            assertEquals("false", w.getBuffer().substring(len, len+=5));
            jw.write(new JsonString("a"));
            assertEquals("\"a\"", w.getBuffer().substring(len, len+3));
        }
    }

    @Test
    void testAggregates() throws IOException {
        try(var w = new StringWriter(); var jw = new JsonWriter(w)) {
            int len = 0;
            jw.write(Basic.of(1.2));
            assertEquals("1.2", w.getBuffer().substring(len, len+=3));
            jw.write(JsonNull.INSTANCE);
            assertEquals("null", w.getBuffer().substring(len, len+=4));
            jw.write(JsonBoolean.TRUE);
            assertEquals("true", w.getBuffer().substring(len, len+=4));
            jw.write(JsonBoolean.FALSE);
            assertEquals("false", w.getBuffer().substring(len, len+=5));
            jw.write(new JsonString("a"));
            assertEquals("\"a\"", w.getBuffer().substring(len, len+3));
        }
    }
}