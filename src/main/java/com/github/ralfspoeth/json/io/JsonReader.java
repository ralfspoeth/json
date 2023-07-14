package com.github.ralfspoeth.json.io;

import com.github.ralfspoeth.json.*;

import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

public class JsonReader implements AutoCloseable {

    private final Lexer lexer;

    public JsonReader(Reader src) {
        this.lexer = new Lexer(src);
    }

    private sealed interface StackElem {
        record BuilderElem(JsonElement.Builder builder) implements StackElem {
        }

        record NameValuePair(String name, JsonElement elem) implements StackElem {
            NameValuePair withElem(JsonElement e) {
                return new NameValuePair(this.name, e);
            }
        }

        enum Char implements StackElem {colon, comma}

        record Root(JsonElement elem) implements StackElem {
        }
    }

    private final Stack<StackElem> stack = new Stack<>();

    public JsonElement readElement() throws IOException {
        while (lexer.hasNext()) {
            var tkn = lexer.next();
            switch (tkn.type()) {
                case STRING -> {
                    var str = token2Value(tkn);
                    if (stack.isEmpty()) {
                        stack.push(new StackElem.Root(str));
                    } else if (stack.top() instanceof StackElem.BuilderElem be) {
                        if (be.builder instanceof JsonElement.JsonObjectBuilder job) {
                            stack.push(new StackElem.NameValuePair(tkn.value(), null));
                        } else if (be.builder instanceof JsonElement.JsonArrayBuilder jab) {
                            jab.item(str);
                        }
                    } else if (stack.top().equals(StackElem.Char.colon)) {
                        stack.pop(); // pop colon
                        stack.swap(se -> StackElem.NameValuePair.class.cast(se).withElem(str));
                    } else if (stack.top().equals(StackElem.Char.comma)) {
                        stack.pop(); // pop comma
                        if (stack.top() instanceof StackElem.BuilderElem be) {
                            if (be.builder instanceof JsonElement.JsonObjectBuilder job) {
                                stack.push(new StackElem.NameValuePair(tkn.value(), null));
                            } else if (be.builder instanceof JsonElement.JsonArrayBuilder jab) {
                                jab.item(str);
                            } else {
                                throw new AssertionError();
                            }
                        }
                    } else {
                        ioex("unexpected token " + tkn.value(), lexer.coordinates());
                    }
                }
                case COLON -> {
                    if (stack.top() instanceof StackElem.NameValuePair nvp && nvp.elem == null) {
                        stack.push(StackElem.Char.colon);
                    } else {
                        ioex("unexpected token :", lexer.coordinates());
                    }
                }
                case COMMA -> {
                    if (stack.top() instanceof StackElem.BuilderElem be
                            && be.builder instanceof JsonElement.JsonArrayBuilder jab
                            && jab.size() > 0
                    ) {
                        stack.push(StackElem.Char.comma);
                    } else if (stack.top() instanceof StackElem.NameValuePair nvp && nvp.elem != null) {
                        stack.pop();
                        if (stack.top() instanceof StackElem.BuilderElem be && be.builder instanceof JsonElement.JsonObjectBuilder job) {
                            job.named(nvp.name, nvp.elem);
                            stack.push(StackElem.Char.comma);
                        }
                    } else {
                        ioex("unexpected token ,", lexer.coordinates());
                    }
                }
                case NULL, FALSE, TRUE, NUMBER -> {
                    var v = token2Value(tkn);
                    if (stack.isEmpty()) {
                        stack.push(new StackElem.Root(v));
                    } else {
                        if (stack.top().equals(StackElem.Char.colon)) {
                            stack.pop(); // pop colon
                            stack.swap(se -> StackElem.NameValuePair.class.cast(se).withElem(v));
                        } else if (stack.top().equals(StackElem.Char.comma)) {
                            stack.pop(); // pop comma
                            if (stack.top() instanceof StackElem.BuilderElem be && be.builder instanceof JsonElement.JsonArrayBuilder jab) { // must be a list
                                jab.item(v);
                            } else {
                                ioex("unexpected token " + tkn.value(), lexer.coordinates());
                            }
                        } else if (stack.top() instanceof StackElem.BuilderElem be && be.builder instanceof JsonElement.JsonArrayBuilder jab) { // must be a list
                            jab.item(v);
                        } else {
                            ioex("unexpected token " + tkn.value(), lexer.coordinates());
                        }
                    }
                }
                case OPENING_BRACE -> {
                    if (stack.isEmpty() || stack.top() instanceof StackElem.BuilderElem be && be.builder instanceof JsonElement.JsonArrayBuilder jab && jab.size() == 0) {
                        stack.push(new StackElem.BuilderElem(JsonElement.objectBuilder()));
                    } else if (EnumSet.allOf(StackElem.Char.class).contains(stack.top())) {
                        stack.pop(); // pop comma or colon
                        stack.push(new StackElem.BuilderElem(JsonElement.objectBuilder()));
                    } else {
                        ioex("unexpected token {", lexer.coordinates());
                    }
                }
                case OPENING_BRACKET -> {
                    if (stack.isEmpty() || stack.top() instanceof StackElem.BuilderElem be && be.builder instanceof JsonElement.JsonArrayBuilder jab && jab.size() == 0) {
                        stack.push(new StackElem.BuilderElem(JsonElement.arrayBuilder()));
                    } else if (EnumSet.allOf(StackElem.Char.class).contains(stack.top())) {
                        stack.pop(); // ignore colon or comma
                        stack.push(new StackElem.BuilderElem(JsonElement.arrayBuilder()));
                    } else {
                        ioex("unexpected token [", lexer.coordinates());
                    }
                }
                case CLOSING_BRACE -> {
                    if (stack.top() instanceof StackElem.NameValuePair nvp && nvp.elem instanceof JsonElement je) {
                        stack.pop();
                        if (stack.top() instanceof StackElem.BuilderElem be && be.builder instanceof JsonElement.JsonObjectBuilder job) {
                            job.named(nvp.name, je);
                        } else {
                            throw new AssertionError();
                        }
                    } else if (stack.top() instanceof StackElem.BuilderElem be && be.builder instanceof JsonElement.JsonObjectBuilder job) {
                        stack.pop();
                        var o = job.build();
                        if (stack.top() instanceof StackElem.BuilderElem abe && abe.builder instanceof JsonElement.JsonArrayBuilder jab) {
                            jab.item(o);
                        } else if (stack.top() instanceof StackElem.NameValuePair nvp && nvp.elem == null) {
                            stack.swap(se -> StackElem.NameValuePair.class.cast(se).withElem(o));
                        } else if (stack.isEmpty()) {
                            stack.push(new StackElem.Root(o));
                        } else {
                            throw new AssertionError();
                        }
                    } else {
                        ioex("unexpected token }", lexer.coordinates());
                    }
                }
                case CLOSING_BRACKET -> {
                    var top = stack.pop();
                    if (top instanceof StackElem.BuilderElem be && be.builder instanceof JsonElement.JsonArrayBuilder jab) {
                        var jsonArray = jab.build();
                        if (stack.top() instanceof StackElem.NameValuePair nvp && nvp.elem == null) {
                            stack.swap(se -> StackElem.NameValuePair.class.cast(se).withElem(jsonArray));
                        } else {
                            stack.push(new StackElem.Root(jsonArray));
                        }
                    } else {
                        throw new AssertionError();
                    }
                }
            }
        }

        var top = stack.pop();
        if (stack.isEmpty()) {
            if (top instanceof StackElem.Root r) {
                return r.elem;
            } else if (top instanceof StackElem.BuilderElem be) {
                return be.builder.build();
            } else {
                throw new AssertionError();
            }
        } else {
            throw new IOException("stack not empty or top-most element not a JsonElement");
        }
    }

    private static JsonValue token2Value(Lexer.Token tkn) {
        return switch (tkn.type()) {
            case NULL -> JsonNull.INSTANCE;
            case TRUE -> JsonBoolean.TRUE;
            case FALSE -> JsonBoolean.FALSE;
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
        lexer.close();
    }
}
