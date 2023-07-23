package com.github.ralfspoeth.json.io;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

class JsonWriterTest {

    @Test
    void toJson() {
    }

    @Test
    void write() {
    }

    @Test
    void minimize() {
        String src = """
                {
                ,
                : "   asdf   ", 5.0000,
                null,
                        true,
                                    false
                                    [  ]
                }
                """;
        String tgt = "{,:\"   asdf   \",5.0,null,true,false[]}";
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