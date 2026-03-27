package io.github.ralfspoeth.json.io;

import io.github.ralfspoeth.json.data.*;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import static io.github.ralfspoeth.json.data.JsonString.escaped;
import static java.util.Objects.requireNonNull;

/**
 * A Writer for JSON values and builders.
 *
 */
public class JsonWriter implements Closeable {

    private final CharSequence indentation;
    private final Writer out;

    private JsonWriter(Writer out, int indentation) {
        var tmp = new char[indentation];
        Arrays.fill(tmp, ' ');
        this.indentation = String.valueOf(tmp);
        this.out = requireNonNull(out);
    }

    /**
     * Instantiate new JsonWriter.
     * @param out the writer
     */
    public JsonWriter(Writer out) {
        this(out, 4);
    }

    /**
     * Serialize the {@link JsonValue} to the {@link Writer}.
     * @param elem the element to serialize, must not be {@code null}
     * @throws IOException when the writer throws
     */
    public void write(JsonValue elem) throws IOException {
        write(requireNonNull(elem), 0);
    }

    /**
     * Serialize the {@link Builder} to the {@link Writer}.
     * @param builder the builder, must not be {@code null}
     * @throws IOException when the writer throws
     */
    public void writeBuilder(Builder<? extends JsonValue> builder) throws IOException {
        write(requireNonNull(builder), 0);
    }

    /*
     *
     */
    private void write(Builder<? extends JsonValue> builder, int level) throws IOException {
        switch (builder) {
            case Builder.BasicBuilder bb -> write(bb.get(), level);
            case Builder.ObjectBuilder ob -> {
                indent(level);
                out.append('{').append('\n');
                var dataIterator = ob.data().entrySet().iterator();
                if (dataIterator.hasNext()) {
                    writeEntry(level, dataIterator);
                    while (dataIterator.hasNext()) {
                        out.append(',');
                        writeEntry(level, dataIterator);
                    }
                    out.append('\n');
                }
                indent(level);
                out.append('}');
            }
            case Builder.ArrayBuilder ab -> {
                indent(level);
                out.append('[');
                var dataIterator = ab.data().iterator();
                if (dataIterator.hasNext()) {
                    write(dataIterator.next(), level);
                    while (dataIterator.hasNext()) {
                        out.append(", ");
                        write(dataIterator.next(), level);
                    }
                }
                out.append(']');
            }
        }
    }

    private void write(JsonValue el, int level) throws IOException {
        switch (el) {
            case JsonObject(var members) -> {
                indent(level);
                out.append('{').append('\n');
                var memberIterator = members.entrySet().iterator();
                if (memberIterator.hasNext()) {
                    writeMember(level, memberIterator);
                    while (memberIterator.hasNext()) {
                        out.append(',');
                        writeMember(level, memberIterator);
                    }
                    out.append('\n');
                }
                indent(level);
                out.append('}');
            }
            case JsonArray(var elements) -> {
                indent(level);
                out.append('[');
                var itemIterator = elements.iterator();
                if (itemIterator.hasNext()) {
                    write(itemIterator.next(), level);
                    while (itemIterator.hasNext()) {
                        out.append(", ");
                        write(itemIterator.next(), level);
                    }
                }
                out.append(']');
            }
            case Basic<?> b -> out.append(b.json());
        }
    }

    private void writeMember(int level, Iterator<Map.Entry<String, JsonValue>> memberIterator) throws IOException {
        var member = memberIterator.next();
        write(escaped(member.getKey()), member.getValue(), level + 1);
    }

    private void writeEntry(int level, Iterator<Map.Entry<String, Builder<? extends JsonValue>>> memberIterator) throws IOException {
        var member = memberIterator.next();
        write(escaped(member.getKey()), member.getValue(), level + 1);
    }

    private void indent(int level) throws IOException {
        for (int i = 0; i < level; i++) {
            out.append(indentation);
        }
    }

    private void write(String name, JsonValue elem, int level) throws IOException {
        indent(level);
        out.append('"').append(name).append('"').append(": ");
        write(elem, level + 1);
    }

    private void write(String name, Builder<? extends JsonValue> elem, int level) throws IOException {
        indent(level);
        out.append('"').append(name).append('"').append(": ");
        write(elem, level + 1);
    }

    @Override
    public void close() throws IOException {
        out.flush();
        out.close();
    }
}