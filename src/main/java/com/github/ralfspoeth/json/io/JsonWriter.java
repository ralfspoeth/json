package com.github.ralfspoeth.json.io;

import com.github.ralfspoeth.json.JsonArray;
import com.github.ralfspoeth.json.Element;
import com.github.ralfspoeth.json.JsonObject;
import com.github.ralfspoeth.json.Basic;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import static java.util.stream.Collectors.joining;

public class JsonWriter {
    private JsonWriter() {
    }

    public static String toJson(Element el) {
        var sb = new StringBuilder();
        switch (el) {
            case Basic v -> v.json();
            case JsonObject o -> sb.append(o.members()
                    .entrySet()
                    .stream()
                    .map(e -> "%s: %s".formatted(e.getKey(), toJson(e.getValue())))
                    .collect(joining(", ", "{", "}")));
            case JsonArray a -> sb.append(a.elements()
                    .stream()
                    .map(JsonWriter::toJson)
                    .collect(joining(", ", "[", "]")));
        }
        return sb.toString();
    }

    public static void write(Element elem, Writer out) throws IOException {
        out.write(toJson(elem));
    }

    public static void minimize(Reader src, Writer target) {
        Lexer.tokenStream(src).forEach(t -> {
            try {
                target.write(switch (t.type()) {
                    case STRING -> '\"' + t.value() + '\"';
                    case NUMBER -> Double.toString(Double.parseDouble(t.value()));
                    default -> t.value();
                });
            } catch (IOException ioex) {
                throw new RuntimeException(ioex);
            }
        });
    }
}