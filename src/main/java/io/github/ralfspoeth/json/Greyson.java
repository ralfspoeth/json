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
     * Parse a JSON source into a {@link JsonValue}
     * if it is complete; may be empty.
     *
     * @param rdr the reader
     * @return a potentially empty representation
     * @throws IOException when the input stream throws
     */
    public static Optional<Builder<? extends JsonValue>> readBuilder(Reader rdr) throws IOException {
        try(var jr = new JsonReader(rdr)) {
            return jr.read();
        }
    }

    /**
     * Shortcut for {@snippet :
     *  readBuilder(rdr).map(Builder::build)
     * }.
     */
    public static Optional<JsonValue> readValue(Reader rdr) throws IOException {
        return readBuilder(rdr).map(Builder::build);
    }


    /**
     * Serialize the {@link JsonValue} to the {@link Writer}.
     * @param writer a writer, must not be {@code null}
     * @param elem the element to serialize, must not be {@code null}
     */
    public static void writeValue(Writer writer, JsonValue elem) throws IOException {
        try(var wrt = new JsonWriter(writer)) {
            wrt.write(requireNonNull(elem));
        }
    }

    /**
     * Serialize the {@link Builder} to the {@link Writer}.
     *
     * @param writer a writer, must not be {@code null}
     * @param builder the builder, must not be {@code null}
     * @throws UncheckedIOException when the writer throws an IOException
     */
    public static void writeBuilder(Writer writer, Builder<? extends JsonValue> builder) throws UncheckedIOException {
        try(var wrt = new JsonWriter(writer)) {
            wrt.write(requireNonNull(builder));
        } catch (IOException ioex) {
            throw new UncheckedIOException(ioex);
        }
    }
}
