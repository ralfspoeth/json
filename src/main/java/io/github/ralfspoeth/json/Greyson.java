package io.github.ralfspoeth.json;

import io.github.ralfspoeth.json.data.JsonValue;
import io.github.ralfspoeth.json.io.JsonReader;
import io.github.ralfspoeth.json.io.JsonWriter;

import java.io.*;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * The Greyson class provides convenient access to common IO operations.
 *
 */
public class Greyson {
    private Greyson() {}

    public static Optional<JsonValue> read(String s) {
        try {
            return read(Reader.of(s));
        } catch (IOException e) {
            throw new AssertionError("StringReader should never throw IOException", e);
        }
    }

    public static Optional<JsonValue> read(Reader rdr) throws IOException {
        try(var jr = new JsonReader(rdr)) {
            return jr.read();
        }
    }

    public static Optional<JsonValue> read(InputStream in) throws IOException {
        try(var jr = new JsonReader(in)) {
            return jr.read();
        }
    }

    /**
     * Serialize the {@link JsonValue} to the {@link Writer}.
     * @param writer a writer, must not be {@code null}
     * @param elem the element to serialize, must not be {@code null}
     */
    public static void write(Writer writer, JsonValue elem) throws IOException {
        try(var wrt = new JsonWriter(writer)) {
            wrt.write(requireNonNull(elem));
        }
    }
}
