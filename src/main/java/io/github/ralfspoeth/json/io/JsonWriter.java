package io.github.ralfspoeth.json.io;

import io.github.ralfspoeth.json.data.Basic;
import io.github.ralfspoeth.json.data.JsonValue;
import io.github.ralfspoeth.json.data.JsonArray;
import io.github.ralfspoeth.json.data.JsonObject;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import static io.github.ralfspoeth.json.data.JsonString.escaped;

public class JsonWriter implements AutoCloseable {

    private final CharSequence indentation;
    private final Writer out;

    public JsonWriter(Writer out, int indentation) {
        var tmp = new char[indentation];
        Arrays.fill(tmp, ' ');
        this.indentation = String.valueOf(tmp);
        this.out = out;
    }

    public JsonWriter(Writer out) {
        this(out, 4);
    }

    public void write(JsonValue elem) throws IOException {
        write(elem, 0);
    }

    private void write(JsonValue el, int level) throws IOException {
        switch (el) {
            case JsonObject(var members)-> {
                indent(level);
                out.append('{').append('\n');
                var memberIterator = members.entrySet().iterator();
                if(memberIterator.hasNext()) {
                    writeMember(level, memberIterator);
                    while(memberIterator.hasNext()) {
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
                if(itemIterator.hasNext()) {
                    write(itemIterator.next(), level);
                    while(itemIterator.hasNext()) {
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

    private void indent(int level) throws IOException {
        for(int i=0;i<level;i++) {
            out.append(indentation);
        }
    }

    private void write(String name, JsonValue elem, int level) throws IOException {
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