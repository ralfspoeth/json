package io.github.ralfspoeth.json;

import java.util.Objects;

public record JsonString(String value) implements Basic<String> {
    public JsonString {
        value = Objects.requireNonNullElse(value, "");
    }

    @Override
    public String json() {
        return "\"%s\"".formatted(escape(value));
    }

    private static String escape(String s) {
        var tmp = s.replaceAll("\\\\", "\\\\\\\\")
                .replaceAll("\"", "\\\\\"")
                .replaceAll("/", "\\\\/")
                .replaceAll("\b", "\\\\b")
                .replaceAll("\f", "\\\\f")
                .replaceAll("\n", "\\\\n")
                .replaceAll("\r", "\\\\r")
                .replaceAll("\t", "\\\\t");
        if(tmp.chars().filter(cp -> 0x00 <= cp && cp <= 0x1F).findFirst().isPresent()) {
            return tmp.chars()
                    .mapToObj(cp -> 0x00 <= cp && cp <= 0x1F?String.format("\\u%04x", cp):String.valueOf((char)cp))
                    .reduce("", String::concat);
        } else {
            return tmp;
        }
    }

    @Override
    public boolean test(String s) {
        return value().equals(s);
    }
}
