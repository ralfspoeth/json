package com.pd.json.io;

import com.pd.json.data.*;

import java.io.IOException;
import java.io.Writer;
import java.util.stream.Collectors;

public class JsonWriter {

    public String toJson(JsonElement el) {
        var sb = new StringBuilder();
        switch (el) {
            case JsonValue v -> {
                switch (v) {
                    case JsonString s -> sb.append('"').append(s.value()).append('"');
                    case JsonNull ignored -> sb.append("null");
                    case JsonTrue ignored -> sb.append("true");
                    case JsonFalse ignored -> sb.append("false");
                    case JsonNumber n -> sb.append(n.value());
                }
            }
            case JsonObject o -> sb.append('{')
                    .append(o.members().entrySet().stream().map(e ->
                            "%s: %s".formatted(
                                    e.getKey(),
                                    toJson(e.getValue())
                            )
                    ).collect(Collectors.joining(", ")))
                    .append('}');
            case JsonArray a -> sb.append('[')
                    .append(a.elements()
                            .stream()
                            .map(this::toJson)
                            .collect(Collectors.joining(", ")))
                    .append(']');
        }
        return sb.toString();
    }

    public void write(JsonElement elem, Writer out) throws IOException {
        out.write(toJson(elem));
    }

}
