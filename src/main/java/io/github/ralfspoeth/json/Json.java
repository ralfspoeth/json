package io.github.ralfspoeth.json;

import io.github.ralfspoeth.json.io.JsonReader;
import io.github.ralfspoeth.json.io.JsonWriter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;
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

    /**
     * Serialize the {@link Element} to the {@link Writer}.
     * @param writer a writer, must not be {@code null}
     * @param elem the element to serialize, must not be {@code null}
     */
    public static void write(Writer writer, Element elem) {
        try(var wrt = JsonWriter.createDefaultWriter(requireNonNull(writer))) {
            wrt.write(requireNonNull(elem));
        }
    }

    /**
     * Serialize the {@link Element} to {@code System.out}.
     * @param elem the element to serialize, must not be {@code null}
     */
    public static void writeToSystemOut(Element elem) {
        write(new PrintWriter(System.out), elem);
    }

    public static Stream<Element> stream(Reader rdr) throws IOException {
        try(var jr = new JsonReader(rdr)) {
            return StreamSupport.stream(
                    spliteratorUnknownSize(jr, ORDERED | NONNULL | IMMUTABLE), false
            );
        }
    }
}
