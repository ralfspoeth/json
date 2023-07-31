package com.github.ralfspoeth.json.io;

import com.github.ralfspoeth.json.JsonArray;
import com.github.ralfspoeth.json.Element;
import com.github.ralfspoeth.json.JsonObject;
import com.github.ralfspoeth.json.Basic;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.joining;

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
                    Map.Entry<String, Element> member;
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
            case Basic b -> out.print(b.json());
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
    public void close() throws IOException {
        out.close();
    }

    public static void minimize(Reader src, Writer target) {
        class Ref {
            Lexer.Token token;
            Ref(Lexer.Token tkn) {
                this.token = tkn;
            }
        }
        Ref last = new Ref(null);
        var separators = Set.of(
                Lexer.TokenType.COLON,
                Lexer.TokenType.COMMA,
                Lexer.TokenType.OPENING_BRACE,
                Lexer.TokenType.CLOSING_BRACE,
                Lexer.TokenType.OPENING_BRACKET,
                Lexer.TokenType.CLOSING_BRACKET
        );
        Lexer.tokenStream(src).forEach(t -> {
            try {
                if(last.token!=null && !separators.contains(last.token.type()) && !separators.contains(t.type())){
                    target.write(' ');
                }
                target.write(switch (t.type()) {
                    case STRING -> '\"' + t.value() + '\"';
                    case NUMBER -> Double.toString(Double.parseDouble(t.value()));
                    default -> t.value();
                });
                last.token = t;
            } catch (IOException ioex) {
                throw new RuntimeException(ioex);
            }
        });
    }
}