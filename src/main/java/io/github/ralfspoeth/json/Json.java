package io.github.ralfspoeth.json;

import io.github.ralfspoeth.json.io.JsonReader;

import java.io.IOException;
import java.io.Reader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Spliterator.*;
import static java.util.Spliterators.spliteratorUnknownSize;

public class Json {
    private Json() {}

    public static Element read(String s) {
        return JsonReader.readElement(s);
    }

    public static Element read(Reader rdr) throws IOException {
        try(var jr = new JsonReader(rdr)) {
            return jr.readElement();
        }
    }

    public static Stream<Element> stream(Reader rdr) throws IOException {
        try(var jr = new JsonReader(rdr)) {
            return StreamSupport.stream(
                    spliteratorUnknownSize(jr, ORDERED | NONNULL | IMMUTABLE), false
            );
        }
    }
}
