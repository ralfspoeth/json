package io.github.ralfspoeth.json.io;

import io.github.ralfspoeth.json.*;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonWriterTest {

    @Test
    void test1() throws Exception {
        var orig = Aggregate.objectBuilder()
                .named("a", new JsonNumber(5))
                .basic("bb", 6d)
                .basic("fuck", null)
                .named("arr", Aggregate.arrayBuilder()
                        .item(Basic.of(null))
                        .item(Basic.of(7.5))
                        .item(Aggregate.objectBuilder().build())
                        .item(Aggregate.objectBuilder().basic("a", 5d).build())
                        .build())
                .build();

        try (var pw = new StringWriter(); var wrt = JsonWriter.createDefaultWriter(new PrintWriter(pw))) {
            wrt.write(orig);
            try(var sr = new StringReader(pw.toString()); var rdr = new JsonReader(sr)) {
                var result = rdr.readElement();
                assertEquals(orig, result);
            }
        }
    }

    @Test
    void testBasics() throws IOException {
        try(var w = new StringWriter(); var jw = JsonWriter.createDefaultWriter(w)) {
            int len = 0;
            jw.write(new JsonNumber(1.2));
            assertEquals("1.2", w.getBuffer().substring(len, len+=3));
            jw.write(JsonNull.INSTANCE);
            assertEquals("null", w.getBuffer().substring(len, len+=4));
            jw.write(JsonBoolean.TRUE);
            assertEquals("true", w.getBuffer().substring(len, len+=4));
            jw.write(JsonBoolean.FALSE);
            assertEquals("false", w.getBuffer().substring(len, len+=5));
            jw.write(new JsonString("a"));
            assertEquals("\"a\"", w.getBuffer().substring(len, len+=3));
        }
    }

    @Test
    void testAggregates() throws IOException {
        try(var w = new StringWriter(); var jw = JsonWriter.createDefaultWriter(w)) {
            int len = 0;
            jw.write(new JsonNumber(1.2));
            assertEquals("1.2", w.getBuffer().substring(len, len+=3));
            jw.write(JsonNull.INSTANCE);
            assertEquals("null", w.getBuffer().substring(len, len+=4));
            jw.write(JsonBoolean.TRUE);
            assertEquals("true", w.getBuffer().substring(len, len+=4));
            jw.write(JsonBoolean.FALSE);
            assertEquals("false", w.getBuffer().substring(len, len+=5));
            jw.write(new JsonString("a"));
            assertEquals("\"a\"", w.getBuffer().substring(len, len+=3));
        }
    }



    @Test
    void minimize() {
        String src = """
                {[],:}{
                ,
                : "   asdf   ", 5.0000,
                null
                        true
                                    false
                                    [  ]
                } 1 2 3
                """;
        String tgt = "{[],:}{,:\"   asdf   \",5.0,null true false[]}1.0 2.0 3.0";
        var wrt = new StringWriter();
        JsonWriter.minimize(new StringReader(src), wrt);
        var wrtOrig = new StringWriter();
        JsonWriter.minimize(new StringReader(tgt), wrtOrig);
        assertAll(
                () -> assertEquals(tgt, wrt.getBuffer().toString()),
                () -> assertEquals(tgt, wrtOrig.getBuffer().toString())
        );
    }
}