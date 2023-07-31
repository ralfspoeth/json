package com.github.ralfspoeth.json.io;

import com.github.ralfspoeth.json.Basic;
import com.github.ralfspoeth.json.JsonNumber;
import org.junit.jupiter.api.Test;

import java.io.*;

import static com.github.ralfspoeth.json.Element.arrayBuilder;
import static com.github.ralfspoeth.json.Element.objectBuilder;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonWriterTest {

    @Test
    void test1() throws Exception {
        var orig = objectBuilder()
                .named("a", new JsonNumber(5))
                .named("bb", Basic.of(6d))
                .namedNull("fuck")
                .named("arr", arrayBuilder()
                        .item(Basic.of(null))
                        .item(Basic.of(7.5))
                        .item(objectBuilder().build())
                        .item(objectBuilder().named("a", 5d).build())
                        .build())
                .build();

        try (var pw = new StringWriter(); var wrt = JsonWriter.createDefaultWriter(new PrintWriter(pw))) {
            wrt.write(orig);
            System.out.println(pw.getBuffer().toString());
            try(var sr = new StringReader(pw.toString()); var rdr = new JsonReader(sr)) {
                var result = rdr.readElement();
                assertEquals(orig, result);
            }
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