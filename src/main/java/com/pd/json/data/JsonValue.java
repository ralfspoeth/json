package com.pd.json.data;

public sealed interface JsonValue extends JsonElement permits JsonString, JsonTrue, JsonFalse, JsonNull, JsonNumber {
    static JsonValue of(String input) {
        return switch (input) {
            case "null" -> JsonNull.INSTANCE;
            case "true" -> JsonTrue.INSTANCE;
            case "false" -> JsonFalse.INSTANCE;
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
}
