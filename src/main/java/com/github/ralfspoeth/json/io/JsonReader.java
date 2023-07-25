package com.github.ralfspoeth.json.io;

import com.github.ralfspoeth.json.*;

import java.io.IOException;
import java.io.Reader;
import java.util.Objects;

import static com.github.ralfspoeth.json.io.JsonReader.V.Char.colon;
import static com.github.ralfspoeth.json.io.JsonReader.V.Char.comma;

public class JsonReader implements AutoCloseable {

    private final Lexer lexer;

    public JsonReader(Reader src) {
        this.lexer = new Lexer(src);
    }

    sealed interface V {
        record ObjBuilderElem(Element.JsonObjectBuilder builder) implements V {
        }

        record ArrBuilderElem(Element.JsonArrayBuilder builder) implements V {
        }

        record NameValuePair(String name, Element elem) implements V {
            NameValuePair withElem(Element e) {
                return new NameValuePair(this.name, e);
            }
        }

        enum Char implements V {colon, comma}

        record Root(Element elem) implements V {
        }
    }

    private final Stack<V> stack = new Stack<>();

    public Element readElement() throws IOException {
        while (lexer.hasNext()) {
            var tkn = lexer.next();
            switch (tkn.type()) {
                case STRING -> {
                    var str = token2Value(tkn);
                    switch (stack.top()) {
                        case null -> stack.push(new V.Root(str));
                        case V.ObjBuilderElem ignored -> stack.push(new V.NameValuePair(tkn.value(), null));
                        case V.ArrBuilderElem abe when abe.builder.size() == 0 -> abe.builder.item(str);
                        case V.Char nc -> {
                            stack.pop();
                            switch (nc) {
                                case colon -> stack.swap(se -> V.NameValuePair.class.cast(se).withElem(str));
                                case comma -> {
                                    stack.top();
                                    switch (stack.top()) {
                                        case V.ObjBuilderElem ignored ->
                                                stack.push(new V.NameValuePair(tkn.value(), null));
                                        case V.ArrBuilderElem(var abe) -> abe.item(str);
                                        default -> throw new IllegalStateException("Unexpected value: " + stack.top());
                                    }
                                }
                            }
                        }
                        default -> throw new IllegalStateException("Unexpected value: " + stack.top());
                    }
                }
                case COLON -> {
                    if (stack.top() instanceof V.NameValuePair nvp && nvp.elem == null) {
                        stack.push(colon);
                    } else {
                        ioex("unexpected token :", lexer.coordinates());
                    }
                }
                case COMMA -> {
                    switch (stack.top()) {
                        case V.ArrBuilderElem ignored -> stack.push(comma);
                        case V.ObjBuilderElem ignored -> stack.push(comma);
                        case V.NameValuePair nvp when nvp.elem != null -> {
                            stack.pop();
                            if (stack.top() instanceof V.ObjBuilderElem be) {
                                be.builder.named(nvp.name, nvp.elem);
                                stack.push(comma);
                            } else {
                                throw new AssertionError();
                            }
                        }
                        default -> ioex("unexpected token ,", lexer.coordinates());
                    }
                }
                case NULL, FALSE, TRUE, NUMBER -> {
                    var v = token2Value(tkn);
                    handle(tkn.value(), v);
                }
                case OPENING_BRACE -> {
                    switch (stack.top()) {
                        case null -> stack.push(new V.ObjBuilderElem(Element.objectBuilder()));
                        case V.ArrBuilderElem ignored -> stack.push(new V.ObjBuilderElem(Element.objectBuilder()));
                        case V.Char ignored -> {
                            stack.pop();
                            stack.push(new V.ObjBuilderElem(Element.objectBuilder()));
                        }
                        default -> ioex("unexpected token " + tkn.value(), lexer.coordinates());
                    }
                }
                case OPENING_BRACKET -> {
                    switch (stack.top()) {
                        case null -> stack.push(new V.ArrBuilderElem(Element.arrayBuilder()));
                        case V.Char ignored -> {
                            stack.pop();
                            stack.push(new V.ArrBuilderElem(Element.arrayBuilder()));
                        }
                        case V.ArrBuilderElem ignored -> stack.push(new V.ArrBuilderElem(Element.arrayBuilder()));
                        default -> ioex("unexpected token " + tkn.value(), lexer.coordinates());
                    }
                }
                case CLOSING_BRACE -> {
                    var obj = switch (stack.top()) {
                        case V.NameValuePair nvp when nvp.elem != null -> {
                            stack.pop(); // remove nvp from top
                            if (stack.top() instanceof V.ObjBuilderElem obe) {
                                stack.pop();
                                obe.builder.named(nvp.name, nvp.elem);
                                yield obe.builder.build();

                            } else {
                                throw new AssertionError();
                            }
                        }
                        case V.ObjBuilderElem obe when obe.builder.size() == 0 -> {
                            stack.pop();
                            yield obe.builder.build();
                        }
                        case null, default -> throw new AssertionError();
                    };
                    handle(tkn.value(), obj);
                }
                case CLOSING_BRACKET -> {
                    var top = stack.pop();
                    if (top instanceof V.ArrBuilderElem abe) {
                        var jsonArray = abe.builder.build();
                        handle(tkn.value(), jsonArray);
                    } else {
                        throw new AssertionError();
                    }
                }
            }
        }

        var top = stack.pop();
        if (stack.isEmpty() && top instanceof V.Root r) {
            return r.elem;
        } else {
            throw new IOException("stack not empty or top-most element not a JsonElement");
        }
    }

    private void handle(String token, Element v) throws IOException {
        switch (stack.top()) {
            case null -> stack.push(new V.Root(v));
            case V.NameValuePair nvp when nvp.elem==null -> stack.swap(se -> nvp.withElem(v));
            case V.Char nc -> {
                switch (nc) {
                    case colon -> {
                        stack.pop(); // pop colon, topmost element mubst be an NVP
                        stack.swap(se -> V.NameValuePair.class.cast(se).withElem(v));
                    }
                    case comma -> {
                        stack.pop(); // pop comma
                        if (Objects.requireNonNull(stack.top()) instanceof V.ArrBuilderElem abe) {
                            abe.builder.item(v);
                        } else {
                            ioex("unexpected token " + token, lexer.coordinates());
                        }
                    }
                }
            }
            case V.ArrBuilderElem abe -> abe.builder.item(v);
            default -> ioex("unexpected token " + token, lexer.coordinates());
        }
    }

    private static Basic token2Value(Lexer.Token tkn) {
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
