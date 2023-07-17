package com.github.ralfspoeth.json;

public sealed interface JsonValue extends JsonElement permits JsonBoolean, JsonNull, JsonNumber, JsonString {

    String json();

    static JsonValue from(String input) {
        return switch (input) {
            case "null" -> JsonNull.INSTANCE;
            case "true" -> JsonBoolean.TRUE;
            case "false" -> JsonBoolean.FALSE;
            default -> {
                if (input.matches("\".*\"")) {
                    var s = input.replaceAll("\\\"", "");
                    if (!s.contains("\"")) {
                        yield new JsonString(input);
                    } else {
                        throw new IllegalArgumentException("Illegal string literal " + input);
                    }
                } else {
                    try {
                        var d = Double.parseDouble(input);
                        yield new JsonNumber(d);
                    } catch (NumberFormatException nfex) {
                        throw new IllegalArgumentException(
                                "%s is neither 'null', 'true', 'false', a number or a string literal"
                                        .formatted(input), nfex
                        );
                    }
                }
            }
        };
    }

    static JsonValue of(Object o) {
        return switch(o) {
            case null -> ofNull();
            case Boolean b -> JsonBoolean.of(b);
            case Double d -> ofDouble(d);
            default -> ofString(o.toString());
        };
    }
    private static JsonNumber ofDouble(double d) {
        return new JsonNumber(d);
    }

    private static JsonNull ofNull() {
        return JsonNull.INSTANCE;
    }

    private static JsonString ofString(String s) {
        return new JsonString(s);
    }
}
