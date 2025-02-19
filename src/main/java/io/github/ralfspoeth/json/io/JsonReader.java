package io.github.ralfspoeth.json.io;

import io.github.ralfspoeth.basix.coll.Stack;
import io.github.ralfspoeth.json.*;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Objects;

import static io.github.ralfspoeth.json.io.JsonReader.Elem.Char.colon;
import static io.github.ralfspoeth.json.io.JsonReader.Elem.Char.comma;

/**
 * Instances parse character streams into JSON {@link Element}s.
 * The class implements {@link AutoCloseable} and is meant to
 * be used in try-with-resources statements like this:
 * {@snippet :
 * // given
 * Reader r = new StringReader(); // @replace substring="new StringReader();" replacement="..."
 * try(var jr = new JsonReader(r)) {
 *     return jr.readElement();
 * }
 * }
 * The class supports strict adherence to the JSON specification
 * exclusively; there is no support for the sloppy variant.
 */
public class JsonReader implements AutoCloseable {

    private final Lexer lexer;

    /**
     * Instantiate this class on top of a {@link Reader}.
     * The source will be closed together with {@code this}.
     *
     * @param src the character source
     */
    public JsonReader(Reader src) {
        this.lexer = new Lexer(src);
    }

    sealed interface Elem {
        record ObjBuilderElem(Aggregate.JsonObjectBuilder builder) implements Elem {
            static ObjBuilderElem empty() {
                return new ObjBuilderElem(Aggregate.objectBuilder());
            }
        }

        record ArrBuilderElem(Aggregate.JsonArrayBuilder builder) implements Elem {
            static ArrBuilderElem empty() {
                return new ArrBuilderElem(Aggregate.arrayBuilder());
            }
        }

        record NameValuePair(String name, Element elem) implements Elem {
            NameValuePair(String name) {
                this(name, null);
            }

            NameValuePair withElem(Element e) {
                return new NameValuePair(this.name, e);
            }
        }

        enum Char implements Elem {colon, comma}

        record Root(Element elem) implements Elem {}
    }

    private final Stack<Elem> stack = new Stack<>();

    /**
     * Parses a fixed string.
     * Uses a {@link StringReader} internally which is passed
     * to a fresh instance of this class.
     *
     * @param s the source string
     * @return the element representing the source string
     */
    public static Element readElement(String s) {
        try (var rdr = new JsonReader(new StringReader(s))) {
            return rdr.readElement();
        } catch (IOException ioex) {
            throw new AssertionError(ioex);
        }
    }

    /**
     * Reads the first and only JSON element from the source.
     *
     * @return the JSON element
     * @throws IOException whenever the underlying source throws
     */
    public Element readElement() throws IOException {
        while (lexer.hasNext()) {
            var tkn = lexer.next();
            switch (tkn.type()) {
                case STRING -> {
                    var str = token2Value(tkn);
                    switch (stack.top()) {
                        case null -> stack.push(new Elem.Root(str));
                        case Elem.ObjBuilderElem ignored -> stack.push(new Elem.NameValuePair(tkn.value()));
                        case Elem.ArrBuilderElem abe when abe.builder.isEmpty() -> abe.builder.item(str);
                        case Elem.Char nc -> {
                            stack.pop();
                            switch (nc) {
                                case colon -> stack.push(((Elem.NameValuePair) stack.pop()).withElem(str));
                                case comma -> {
                                    switch (stack.top()) {
                                        case Elem.ObjBuilderElem ignored ->
                                                stack.push(new Elem.NameValuePair(tkn.value()));
                                        case Elem.ArrBuilderElem(var abe) -> abe.item(str);
                                        default -> throw new IllegalStateException("Unexpected value: " + stack.top());
                                    }
                                }
                            }
                        }
                        default -> throw new IllegalStateException("Unexpected value: " + stack.top());
                    }
                }
                case COLON -> {
                    if (stack.top() instanceof Elem.NameValuePair nvp && nvp.elem == null) {
                        stack.push(colon);
                    } else {
                        ioex("unexpected token :", lexer.coordinates());
                    }
                }
                case COMMA -> {
                    switch (stack.top()) {
                        case Elem.ArrBuilderElem ignored -> stack.push(comma);
                        case Elem.ObjBuilderElem ignored -> stack.push(comma);
                        case Elem.NameValuePair nvp when nvp.elem != null -> {
                            stack.pop();
                            if (stack.top() instanceof Elem.ObjBuilderElem(var builder)) {
                                builder.named(nvp.name, nvp.elem);
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
                        case null -> stack.push(Elem.ObjBuilderElem.empty());
                        case Elem.ArrBuilderElem ignored -> stack.push(Elem.ObjBuilderElem.empty());
                        case Elem.Char ignored -> {
                            stack.pop();
                            stack.push(Elem.ObjBuilderElem.empty());
                        }
                        default -> ioex("unexpected token " + tkn.value(), lexer.coordinates());
                    }
                }
                case OPENING_BRACKET -> {
                    switch (stack.top()) {
                        case null -> stack.push(Elem.ArrBuilderElem.empty());
                        case Elem.Char ignored -> {
                            stack.pop();
                            stack.push(Elem.ArrBuilderElem.empty());
                        }
                        case Elem.ArrBuilderElem ignored -> stack.push(Elem.ArrBuilderElem.empty());
                        default -> ioex("unexpected token " + tkn.value(), lexer.coordinates());
                    }
                }
                case CLOSING_BRACE -> {
                    var obj = switch (stack.top()) {
                        case Elem.NameValuePair nvp when nvp.elem != null -> {
                            stack.pop(); // remove nvp from top
                            if (stack.top() instanceof Elem.ObjBuilderElem(var builder)) {
                                stack.pop();
                                builder.named(nvp.name, nvp.elem);
                                yield builder.build();

                            } else {
                                throw new AssertionError();
                            }
                        }
                        case Elem.ObjBuilderElem obe when obe.builder.isEmpty() -> {
                            stack.pop();
                            yield obe.builder.build();
                        }
                        case null, default -> throw new AssertionError();
                    };
                    handle(tkn.value(), obj);
                }
                case CLOSING_BRACKET -> {
                    var top = stack.pop();
                    if (top instanceof Elem.ArrBuilderElem(var builder)) {
                        var jsonArray = builder.build();
                        handle(tkn.value(), jsonArray);
                    } else {
                        throw new AssertionError();
                    }
                }
            }
        }

        var top = stack.pop();
        if (stack.isEmpty() && top instanceof Elem.Root(var elem)) {
            return elem;
        } else {
            throw new IOException("stack not empty or top-most element not a JsonElement");
        }
    }

    private void handle(String token, Element v) throws IOException {
        switch (stack.top()) {
            case null -> stack.push(new Elem.Root(v));
            case Elem.NameValuePair nvp when nvp.elem == null -> {
                stack.pop();
                stack.push(nvp.withElem(v));
            }
            case Elem.Char nc -> {
                switch (nc) {
                    case colon -> {
                        stack.pop(); // pop colon, topmost element must be an NVP
                        stack.push(((Elem.NameValuePair) stack.pop()).withElem(v));
                    }
                    case comma -> {
                        stack.pop(); // pop comma
                        if (Objects.requireNonNull(stack.top()) instanceof Elem.ArrBuilderElem(var builder)) {
                            builder.item(v);
                        } else {
                            ioex("unexpected token " + token, lexer.coordinates());
                        }
                    }
                }
            }
            case Elem.ArrBuilderElem(var builder) -> builder.item(v);
            default -> ioex("unexpected token " + token, lexer.coordinates());
        }
    }

    private static Basic<?> token2Value(Lexer.Token tkn) {
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
