package io.github.ralfspoeth.json;

import io.github.ralfspoeth.json.data.Builder;
import io.github.ralfspoeth.json.data.JsonValue;
import io.github.ralfspoeth.json.io.JsonReader;
import io.github.ralfspoeth.json.io.JsonWriter;

import java.io.*;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * The <b>Greyson</b> class provides static read and write operations
 * for JSON input and output streams.
 * The class is the starting point when dealing with incoming JSON data,
 * and the end point when submitting JSON data to some receiver.
 * {@link #readValue(Reader)} reads JSON data into
 * {@link JsonValue}s, and {@link #writeValue(Writer, JsonValue)} writes
 * {@link JsonValue}s to output streams.
 * {@link #readBuilder(Reader)} reads JSON data into
 * a builder instance meant to be manipulated "on the fly", where
 * The {@link #writeValue(Writer, JsonValue)} and {@link #writeBuilder(Writer, Builder)}
 * methods throw unchecked exceptions as they
 * are assumed to be used in fluent invocation chains.
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
    public static void writeValue(Writer writer, JsonValue elem) throws UncheckedIOException {
        try(var wrt = new JsonWriter(writer)) {
            wrt.write(requireNonNull(elem));
        } catch(IOException e) {
            throw new UncheckedIOException(e);
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
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
