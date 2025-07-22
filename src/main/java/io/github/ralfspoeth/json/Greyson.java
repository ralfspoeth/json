package io.github.ralfspoeth.json;

import io.github.ralfspoeth.json.io.JsonReader;
import io.github.ralfspoeth.json.io.JsonWriter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class Greyson {
    private Greyson() {}

    public static JsonValue read(String s) {
        return JsonReader.readElement(s);
    }

    public static JsonValue read(Reader rdr) throws IOException {
        try(var jr = new JsonReader(rdr)) {
            return jr.readElement();
        }
    }

    /**
     * Serialize the {@link JsonValue} to the {@link Writer}.
     * @param writer a writer, must not be {@code null}
     * @param elem the element to serialize, must not be {@code null}
     */
    public static void write(Writer writer, JsonValue elem) {
        try(var wrt = JsonWriter.createDefaultWriter(requireNonNull(writer))) {
            wrt.write(requireNonNull(elem));
        }
    }

    /**
     * Serialize the {@link JsonValue} to {@code System.out}.
     * @param elem the element to serialize, must not be {@code null}
     */
    public static void writeToSystemOut(JsonValue elem) {
        write(new PrintWriter(System.out), elem);
    }

    public static Stream<JsonValue> stream(Reader rdr) throws IOException {
        return JsonReader.stream(rdr);
    }
}
