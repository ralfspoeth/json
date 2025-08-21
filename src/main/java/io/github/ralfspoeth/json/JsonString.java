package io.github.ralfspoeth.json;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Objects.requireNonNull;

public value record JsonString(String value) implements Basic<String> {
    public JsonString {
        value = requireNonNull(value);
    }

    @Override
    public String json() {
        return "\"%s\"".formatted(escaped(value));
    }

    private static final ConcurrentMap<String, String> cachedEscaped = new ConcurrentHashMap<>();

    public static String escaped(String s) {
        return cachedEscaped.computeIfAbsent(s, JsonString::escape);
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
                    .mapToObj(cp -> 0x00 <= cp && cp <= 0x1F
                            ?"\\u%04x".formatted(cp)
                            :String.valueOf((char)cp))
                    .reduce("", String::concat);
        } else {
            return tmp;
        }
    }

    @Override
    public boolean test(JsonValue s) {
        return s instanceof JsonString(String v) && v.equals(value);
    }
}
