package com.pd.json.io;

import com.pd.json.data.*;

import java.io.IOException;
import java.util.*;

import static java.util.stream.Collectors.toMap;

public class Reader implements AutoCloseable {
    record NameValuePair(String name, JsonElement value) {
    }

    private final Lexer lexer;

    public Reader(Lexer lexer) {
        this.lexer = lexer;
    }

    private final Deque<Object> stack = new LinkedList<>();

    public JsonElement readElement() throws IOException {

        while (lexer.hasNext()) {
            var tkn = lexer.next();
            switch (tkn.type()) {
                case STRING -> {
                    var str = valueOf(tkn);
                    if (stack.isEmpty()) {
                        stack.push(str);
                    } else if (stack.peek() instanceof Map<?, ?>) {
                        stack.push(new NameValuePair(tkn.value(), null));
                    } else if (stack.peek() instanceof List l) {
                        l.add(str);
                    } else if (stack.peek() instanceof String s && s.equals(":")) {
                        stack.pop(); // pop colon
                        var nvp = (NameValuePair) stack.pop();
                        stack.push(new NameValuePair(nvp.name, new JsonString(tkn.value())));
                    } else if (stack.peek() instanceof String s && s.equals(",")) {
                        stack.pop(); // pop comma
                        if (stack.peek() instanceof List l) {
                            l.add(str);
                        } else if (stack.peek() instanceof Map<?, ?>) {
                            stack.push(new NameValuePair(tkn.value(), null));
                        } else {
                            throw new AssertionError();
                        }
                    } else {
                        throw new IOException("unexpected token " + tkn.value());
                    }
                }
                case COLON -> {
                    if (stack.peek() instanceof NameValuePair nvp && nvp.value == null) {
                        stack.push(":");
                    } else {
                        throw new IOException("unexpected token : at coordinates %d and %d"
                                .formatted(lexer.coordinates()[0], lexer.coordinates()[1]));
                    }
                }
                case COMMA -> {
                    if (stack.peek() instanceof List l && !l.isEmpty()) {
                        stack.push(",");
                    } else if (stack.peek() instanceof NameValuePair nvp && nvp.value != null) {
                        stack.pop();
                        var m = (Map<String, Object>) stack.pop();
                        m.put(nvp.name, nvp.value);
                        stack.push(",");
                    } else {
                        throw new IOException("unexpected token ,");
                    }
                }
                case NULL, FALSE, TRUE, NUMBER -> {
                    var v = valueOf(tkn);
                    if (stack.peek() instanceof String s && s.equals(":")) {
                        stack.pop(); // pop colon
                        var nvp = (NameValuePair) stack.pop(); // must be an NVP
                        stack.push(new NameValuePair(nvp.name, v));
                    } else if (stack.peek() instanceof String s && s.equals(",")) {
                        stack.pop(); // pop comma
                        if (stack.peek() instanceof List l) { // must be a list
                            l.add(v);
                        } else {
                            throw new AssertionError();
                        }
                    } else if (stack.isEmpty()) {
                        stack.push(v);
                    } else if (stack.peek() instanceof List l && l.isEmpty()) {
                        l.add(v);
                    } else {
                        throw new IOException("unexpected token " + tkn.value());
                    }
                }
                case OPENING_BRACE -> {
                    if (stack.isEmpty() || stack.peek() instanceof List l && l.isEmpty()) {
                        stack.push(new HashMap<String, Object>());
                    } else if (Set.of(":", ",").contains(stack.peek())) {
                        stack.pop(); // pop comma or colon
                        stack.push(new HashMap<String, Object>());
                    } else {
                        throw new IOException("unexpected token {");
                    }
                }
                case OPENING_BRACKET -> {
                    if (stack.isEmpty() || stack.peek() instanceof List l && l.isEmpty()) {
                        stack.push(new ArrayList<>());
                    } else if (Set.of(":", ",").contains(stack.peek())) {
                        stack.pop(); // ignore colon or comma
                        stack.push(new ArrayList<>());
                    } else {
                        throw new IOException("unexpected token [");
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
                        var o = objectOfMap(m);
                        if (stack.peek() instanceof List l) {
                            l.add(o);
                        } else if(stack.peek() instanceof NameValuePair nvp && nvp.value == null) {
                            stack.pop();
                            stack.push(new NameValuePair(nvp.name, o));
                        } else if (stack.isEmpty()) {
                            stack.push(o);
                        } else {
                            throw new AssertionError();
                        }
                    } else {
                        throw new IOException("unexpected token }");
                    }
                }
                case CLOSING_BRACKET -> {
                    var top = stack.pop();
                    if (top instanceof List<?> l) {
                        var ll = new JsonArray(l.stream().map(JsonElement.class::cast).toList());
                        if(stack.peek() instanceof NameValuePair nvp && nvp.value == null) {
                            stack.pop();
                            stack.push(new NameValuePair(nvp.name, ll));
                        }
                        else {
                            stack.push(ll);
                        }
                    } else {
                        throw new AssertionError();
                    }
                }
            }
        }

        var top = stack.pop();
        if (stack.isEmpty() && top instanceof
                JsonElement je) {
            return je;
        } else {
            throw new IOException("unexpected enf of file");
        }

    }

    private static JsonObject objectOfMap(Map<?, ?> m) {
        return new JsonObject(m.entrySet().stream().collect(
                toMap(e -> (String) e.getKey(), e -> (JsonElement) e.getValue()))
        );
    }

    private static JsonValue valueOf(Lexer.Token tkn) {
        return switch (tkn.type()) {
            case NULL -> new JsonNull();
            case TRUE -> new JsonTrue();
            case FALSE -> new JsonFalse();
            case STRING -> new JsonString(tkn.value());
            case NUMBER -> new JsonNumber(Double.parseDouble(tkn.value()));
            default -> throw new AssertionError();
        };
    }

    private static boolean isLiteral(Lexer.Token t) {
        return EnumSet.of(
                Lexer.TokenType.FALSE,
                Lexer.TokenType.TRUE,
                Lexer.TokenType.NULL,
                Lexer.TokenType.STRING,
                Lexer.TokenType.NUMBER
        ).contains(t.type());
    }

    @Override
    public void close() throws IOException {
        this.lexer.close();
    }
}
