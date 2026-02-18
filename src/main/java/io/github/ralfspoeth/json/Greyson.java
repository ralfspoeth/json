package io.github.ralfspoeth.json;

import io.github.ralfspoeth.json.data.Builder;
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

    /**
     * Parse the contents which the reader produces
     * into a {@link Builder}.
     *
     * @param reader the source
     * @return a builder instance
     * @throws IOException upon IO exceptions
     */
    public static Optional<Builder<?>> readBuilder(Reader reader) throws IOException {
        try(var jr = new JsonReader(reader)) {
            return jr.readBuilder();
        }
    }

    /**
     * Parse a JSON source into a {@link JsonValue}
     * if it is complete; may be empty.
     *
     * @param rdr the reader
     * @return a potentially empty representation
     * @throws IOException when the input stream throws
     */
    public static Optional<JsonValue> read(Reader rdr) throws IOException {
        try(var jr = new JsonReader(rdr)) {
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
