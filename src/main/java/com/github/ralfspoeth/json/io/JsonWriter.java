package com.github.ralfspoeth.json.io;

import com.github.ralfspoeth.json.*;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.stream.Collectors;

public class JsonWriter {

    public String toJson(JsonElement el) {
        var sb = new StringBuilder();
        switch (el) {
            case JsonValue v -> v.json();
            case JsonObject o -> sb.append(o.members()
                    .entrySet()
                    .stream()
                    .map(e -> "%s: %s".formatted(e.getKey(), toJson(e.getValue())))
                    .collect(Collectors.joining(", ", "{", "}")));
            case JsonArray a -> sb.append(a.elements()
                    .stream()
                    .map(this::toJson)
                    .collect(Collectors.joining(", ", "[", "]")));
        }
        return sb.toString();
    }

    public void write(JsonElement elem, Writer out) throws IOException {
        out.write(toJson(elem));
    }

    public static void minimize(Reader src, Writer target) {
        Lexer.tokenStream(src).forEach(t -> {
            try {
                target.write(t.value());
            }
            catch (IOException ioex) {
                throw new RuntimeException(ioex);
            }
        });
    }
}