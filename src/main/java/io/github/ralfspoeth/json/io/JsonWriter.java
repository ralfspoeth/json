package io.github.ralfspoeth.json.io;

import io.github.ralfspoeth.json.Basic;
import io.github.ralfspoeth.json.Element;
import io.github.ralfspoeth.json.JsonArray;
import io.github.ralfspoeth.json.JsonObject;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

public class JsonWriter implements AutoCloseable {

    private final int indentation;
    private final PrintWriter out;

    private JsonWriter(int indentation, Writer out) {
        this.indentation = indentation;
        this.out = out instanceof PrintWriter pw?pw:new PrintWriter(out);
    }

    public static JsonWriter createDefaultWriter(Writer out) {
        return new JsonWriter(4, out);
    }

    public void write(Element elem) {
        write(elem, 0);
    }

    private void write(Element el, int level) {
        char[] chars = indentationChars(level);
        switch (el) {
            case JsonObject jo -> {
                out.println('{');
                var memberIterator = jo.members().entrySet().iterator();
                if(memberIterator.hasNext()) {
                    writeMember(level, memberIterator);
                    while(memberIterator.hasNext()) {
                        out.println(',');
                        writeMember(level, memberIterator);
                    }
                    out.println();
                }
                out.print(chars);
                out.print('}');
            }
            case JsonArray ja -> {
                out.print('[');
                var itemIterator = ja.elements().iterator();
                if(itemIterator.hasNext()) {
                    write(itemIterator.next(), level);
                    while(itemIterator.hasNext()) {
                        out.print(", ");
                        write(itemIterator.next(), level);
                    }
                }
                out.print(']');
            }
            case Basic<?> b -> out.print(b.json());
        }
    }

    private void writeMember(int level, Iterator<Map.Entry<String, Element>> memberIterator) {
        var member = memberIterator.next();
        write(member.getKey(), member.getValue(), level + 1);
    }

    private char[] indentationChars(int level) {
        var chars = new char[level * indentation];
        Arrays.fill(chars, ' ');
        return chars;
    }

    private void write(String name, Element elem, int level) {
        var chars = indentationChars(level);
        out.print(chars);
        out.print('"');
        out.print(name);
        out.print('"');
        out.print(": ");
        write(elem, level + 1);
    }

    @Override
    public void close() {
        out.close();
    }
}