package com.pd.json.io;

import com.pd.json.data.*;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

import static java.util.stream.Collectors.toMap;

public class JsonReader implements AutoCloseable {
    record NameValuePair(String name, JsonElement value) {
    }

    private final Lexer lexer;

    public JsonReader(Reader src) {
        this.lexer = new Lexer(src);
    }

    private final Deque<Object> stack = new LinkedList<>();

    public JsonElement readElement() throws IOException {
        while (lexer.hasNext()) {
            var tkn = lexer.next();
            switch (tkn.type()) {
                case STRING -> {
                    var str = token2Value(tkn);
                    if (stack.isEmpty()) {
                        stack.push(str);
                    } else if (stack.peek() instanceof Map<?, ?>) {
                        stack.push(new NameValuePair(tkn.value(), null));
                    } else if (stack.peek() instanceof List<?> l) {
                        ((List<Object>)l).add(str);
                    } else if (stack.peek() instanceof String s && s.equals(":")) {
                        stack.pop(); // pop colon
                        var nvp = (NameValuePair) stack.pop();
                        stack.push(new NameValuePair(nvp.name, new JsonString(tkn.value())));
                    } else if (stack.peek() instanceof String s && s.equals(",")) {
                        stack.pop(); // pop comma
                        if (stack.peek() instanceof List<?> l) {
                            ((List<Object>)l).add(str);
                        } else if (stack.peek() instanceof Map<?, ?>) {
                            stack.push(new NameValuePair(tkn.value(), null));
                        } else {
                            throw new AssertionError();
                        }
                    } else {
                        ioex("unexpected token " + tkn.value(), lexer.coordinates());
                    }
                }
                case COLON -> {
                    if (stack.peek() instanceof NameValuePair nvp && nvp.value == null) {
                        stack.push(":");
                    } else {
                        ioex("unexpected token :", lexer.coordinates());
                    }
                }
                case COMMA -> {
                    if (stack.peek() instanceof List<?> l && !l.isEmpty()) {
                        stack.push(",");
                    } else if (stack.peek() instanceof NameValuePair nvp && nvp.value != null) {
                        stack.pop();
                        var m = (Map<String, Object>) stack.peek();
                        m.put(nvp.name, nvp.value);
                        stack.push(",");
                    } else {
                        ioex("unexpected token ,", lexer.coordinates());
                    }
                }
                case NULL, FALSE, TRUE, NUMBER -> {
                    var v = token2Value(tkn);
                    if (stack.peek() instanceof String s && s.equals(":")) {
                        stack.pop(); // pop colon
                        var nvp = (NameValuePair) stack.pop(); // must be an NVP
                        stack.push(new NameValuePair(nvp.name, v));
                    } else if (stack.peek() instanceof String s && s.equals(",")) {
                        stack.pop(); // pop comma
                        if (stack.peek() instanceof List<?> l) { // must be a list
                            ((List<Object>)l).add(v);
                        } else {
                            throw new AssertionError();
                        }
                    } else if (stack.isEmpty()) {
                        stack.push(v);
                    } else if (stack.peek() instanceof List<?> l && l.isEmpty()) {
                        ((List<Object>)l).add(v);
                    } else {
                        ioex("unexpected token " + tkn.value(), lexer.coordinates());
                    }
                }
                case OPENING_BRACE -> {
                    if (stack.isEmpty() || stack.peek() instanceof List<?> l && l.isEmpty()) {
                        stack.push(new HashMap<String, Object>());
                    } else if (Set.of(":", ",").contains(stack.peek())) {
                        stack.pop(); // pop comma or colon
                        stack.push(new HashMap<String, Object>());
                    } else {
                        ioex("unexpected token {", lexer.coordinates());
                    }
                }
                case OPENING_BRACKET -> {
                    if (stack.isEmpty() || stack.peek() instanceof List<?> l && l.isEmpty()) {
                        stack.push(new ArrayList<>());
                    } else if (Set.of(":", ",").contains(stack.peek())) {
                        stack.pop(); // ignore colon or comma
                        stack.push(new ArrayList<>());
                    } else {
                        ioex("unexpected token [", lexer.coordinates());
                    }
                }
                case CLOSING_BRACE -> {
                    var top = stack.pop();
                    if (top instanceof NameValuePair nvp && nvp.value instanceof JsonElement je) {
                        if (stack.peek() instanceof Map<?, ?>) {
                            top = stack.pop();
                            ((Map<String, Object>) top).put(nvp.name, je);
                        } else {
                            throw new AssertionError();
                        }
                    }
                    if (top instanceof Map<?, ?> m) {
                        var o = map2Object(m);
                        if (stack.peek() instanceof List<?> l) {
                            ((List<Object>)l).add(o);
                        } else if (stack.peek() instanceof NameValuePair nvp && nvp.value == null) {
                            stack.pop();
                            stack.push(new NameValuePair(nvp.name, o));
                        } else if (stack.isEmpty()) {
                            stack.push(o);
                        } else {
                            throw new AssertionError();
                        }
                    } else {
                        ioex("unexpected token }", lexer.coordinates());
                    }
                }
                case CLOSING_BRACKET -> {
                    var top = stack.pop();
                    if (top instanceof List<?> l) {
                        var ll = new JsonArray(l.stream().map(JsonElement.class::cast).toList());
                        if (stack.peek() instanceof NameValuePair nvp && nvp.value == null) {
                            stack.pop();
                            stack.push(new NameValuePair(nvp.name, ll));
                        } else {
                            stack.push(ll);
                        }
                    } else {
                        throw new AssertionError();
                    }
                }
            }
        }

        var top = stack.pop();
        if (stack.isEmpty() && top instanceof JsonElement je) {
            return je;
        } else {
            throw new IOException("stack not empty or top-most element not a JsonElement");
        }
    }

    private static JsonObject map2Object(Map<?, ?> m) {
        return new JsonObject(m.entrySet().stream().collect(
                toMap(e -> (String) e.getKey(), e -> (JsonElement) e.getValue()))
        );
    }

    private static JsonValue token2Value(Lexer.Token tkn) {
        return switch (tkn.type()) {
            case NULL -> new JsonNull();
            case TRUE -> new JsonTrue();
            case FALSE -> new JsonFalse();
            case STRING -> new JsonString(tkn.value());
            case NUMBER -> new JsonNumber(Double.parseDouble(tkn.value()));
            default -> throw new AssertionError();
        };
    }

    private static void ioex(String msg, Lexer.Coordinates coordinates) throws IOException {
        throw new IOException("%s at row: %d, column: %d".formatted(msg, coordinates.row(), coordinates.column()));
    }

    @Override
    public void close() throws IOException {
        this.lexer.close();
    }
}