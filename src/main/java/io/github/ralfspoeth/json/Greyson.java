package io.github.ralfspoeth.json;

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

    public static JsonValue readValue(String s) {
        return read(s).orElseThrow();
    }

    public static JsonValue readValue(Reader rdr) throws IOException {
        return read(rdr).orElseThrow();
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

    /**
     * Serialize the {@link JsonValue} to {@code System.out}.
     * @param elem the element to serialize, must not be {@code null}
     */
    public static void writeToSystemOut(JsonValue elem) throws IOException {
        write(new PrintWriter(System.out), elem);
    }

    /**
     * Serialize the {@link JsonValue} into a {@link StringBuilder}.
     * The string builder is returned for convenient use:
     * {@snippet :
     * // Given
     * JsonValue jv = null; // @replace regex="null;" replacement="..."
     * // we can then chain things like `toString` easily, so instead of
     * var sb = new StringBuilder();
     * Greyson.writeToStringBuilder(sb, jv);
     * var s = sb.toString();
     * // we may write
     * var s = Greyson.writeToStringBuilder(new StringBuilder(), jv).toString();
     * }
     * @param elem the element to serialize, must not be {@code null}
     * @param sb a string builder
     * @return the string builder provided
     */
    public static StringBuilder write(StringBuilder sb, JsonValue elem) {
        try {
            write(new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) {
                    sb.append(cbuf, off, len);
                }
                @Override
                public void flush() {}
                @Override
                public void close() {}
            }, elem);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return sb;
    }
}
