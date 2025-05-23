package io.github.ralfspoeth.json.io;

import io.github.ralfspoeth.basix.coll.Stack;
import io.github.ralfspoeth.json.*;
import io.github.ralfspoeth.json.Aggregate.JsonArrayBuilder;
import io.github.ralfspoeth.json.Aggregate.JsonObjectBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;
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
 *     return jr.readNextElement();
 * }
 *}
 * The class supports strict adherence to the JSON specification
 * exclusively; there is no support for the sloppy variant.
 */
public class JsonReader implements AutoCloseable, Iterator<Element> {

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
        record ObjBuilderElem(JsonObjectBuilder builder) implements Elem {
            static ObjBuilderElem empty() {
                return new ObjBuilderElem(Aggregate.objectBuilder());
            }
        }

        record ArrBuilderElem(JsonArrayBuilder builder) implements Elem {
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

        record Root(Element elem) implements Elem {
        }
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
            throw new RuntimeException(ioex);
        }
    }

    /**
     * Reads the first and only JSON element from the source.
     *
     * @return the JSON element
     * @throws IOException whenever the underlying source throws
     */
    public Element readElement() throws IOException {
        var result = readNextElement();
        if(lexer.hasNext()) {
            throw new JsonParseException("Input contains tokens after the first element", lexer.coordinates().row(), lexer.coordinates().column());
        } else {
            return result;
        }
    }

    private Element readNextElement() throws IOException {
        // repeat to take the next token while the lexer has more tokens available
        // and either the stack is empty or,
        // in case we expect to read more than one JSON element from the potentially unbounded source,
        // the top element is not a root element
        while (lexer.hasNext() && (stack.isEmpty() || !stack.top().getClass().equals(Elem.Root.class))) {
            var tkn = lexer.next();
            // we switch over the type of the token as the primary level compound state
            switch (tkn.type()) {
                // a colon is acceptable if and only if the current element at the
                // top of the stack is a name-value-pair
                case COLON -> {
                    if (stack.top() instanceof Elem.NameValuePair nvp && nvp.elem == null) {
                        stack.push(colon);
                    } else {
                        parseException("unexpected token : " + tkn, lexer.coordinates());
                    }
                }
                // a comma separates elements in an aggregate,
                // that is, the top of the stack must be a non-empty
                // aggregate element
                case COMMA -> {
                    switch (stack.top()) {
                        case Elem.ArrBuilderElem abe when !abe.builder.isEmpty() -> stack.push(comma);
                        case Elem.ObjBuilderElem obe when !obe.builder.isEmpty() -> stack.push(comma);
                        case Elem.NameValuePair nvp when nvp.elem != null -> {
                            stack.pop();
                            if (stack.top() instanceof Elem.ObjBuilderElem(var builder)) {
                                builder.named(nvp.name, nvp.elem);
                                stack.push(comma);
                            } else {
                                parseException("unexpected token: " + tkn, lexer.coordinates());
                            }
                        }
                        case null, default -> parseException("unexpected token: " + tkn, lexer.coordinates());
                    }
                }
                case NULL, FALSE, TRUE, NUMBER, STRING -> {
                    if (tkn.type() == Lexer.Type.STRING &&
                        stack.top() instanceof Elem.ObjBuilderElem(var builder) && builder.isEmpty()
                    ) {
                        stack.push(new Elem.NameValuePair(tkn.value()));
                    } else if (tkn.type() == Lexer.Type.STRING && comma.equals(stack.top())) {
                        stack.pop();
                        switch (stack.top()) {
                            case Elem.ObjBuilderElem ignored -> stack.push(new Elem.NameValuePair(tkn.value()));
                            case Elem.ArrBuilderElem abe -> abe.builder.item(new JsonString(tkn.value()));
                            case null, default -> parseException("Unexpected value: " + tkn, lexer.coordinates());
                        }
                    } else {
                        var v = token2Value(tkn);
                        handle(tkn.value(), v);
                    }
                }
                case OPENING_BRACE -> {
                    switch (stack.top()) {
                        case null -> stack.push(Elem.ObjBuilderElem.empty());
                        case Elem.ArrBuilderElem(var builder)
                                when builder.isEmpty() -> stack.push(Elem.ObjBuilderElem.empty());
                        case Elem.Char ignored -> stack.push(Elem.ObjBuilderElem.empty());
                        default -> parseException("unexpected token " + tkn.value(), lexer.coordinates());
                    }
                }
                case OPENING_BRACKET -> {
                    switch (stack.top()) {
                        case null -> stack.push(Elem.ArrBuilderElem.empty());
                        case Elem.Char ignored -> stack.push(Elem.ArrBuilderElem.empty());
                        case Elem.ArrBuilderElem(var builder) when builder.isEmpty() -> stack.push(Elem.ArrBuilderElem.empty());
                        default -> parseException("unexpected token " + tkn.value(), lexer.coordinates());
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
                                parseException("unexpected token: " + tkn, lexer.coordinates());
                                yield null;
                            }
                        }
                        case Elem.ObjBuilderElem obe when obe.builder.isEmpty() -> {
                            stack.pop();
                            yield obe.builder.build();
                        }
                        case null, default -> {
                            parseException("unexpected token: " + tkn, lexer.coordinates());
                            yield null;
                        }
                    };
                    handle(tkn.value(), obj);
                }
                case CLOSING_BRACKET -> {
                    var top = stack.pop();
                    if (top instanceof Elem.ArrBuilderElem(var builder)) {
                        var jsonArray = builder.build();
                        handle(tkn.value(), jsonArray);
                    } else {
                        parseException("unexpected token: " + tkn, lexer.coordinates());
                    }
                }
            }
        }

        if (stack.isEmpty()) {
            return null;
        } else if (stack.pop() instanceof Elem.Root(var elem)) {
            return elem;
        } else {
            parseException("stack not empty or top-most element not a JsonElement", lexer.coordinates());
            return null;
        }
    }

    private void handle(String token, Element v) {
        switch (stack.top()) {
            // stack is empty
            case null -> stack.push(new Elem.Root(v));
            // colon or comma at the top
            case Elem.Char nc -> {
                switch (nc) {
                    // colon: name-value pair is second on top
                    case colon -> {
                        stack.pop(); // pop colon, topmost element must be an NVP
                        if (stack.pop() instanceof Elem.NameValuePair nvp) {
                            stack.push(nvp.withElem(v));
                        } else {
                            parseException("unexpected token: " + token, lexer.coordinates());
                        }
                    }
                    // comma on top
                    case comma -> {
                        stack.pop(); // pop comma
                        if (Objects.requireNonNull(stack.top()) instanceof Elem.ArrBuilderElem(var builder)) {
                            builder.item(v);
                        } else {
                            parseException("unexpected token " + token, lexer.coordinates());
                        }
                    }
                }
            }
            case Elem.ArrBuilderElem(var builder) when builder.isEmpty() -> builder.item(v);
            default -> parseException("unexpected token " + token, lexer.coordinates());
        }
    }

    private static Basic<?> token2Value(Lexer.Token tkn) {
        return switch (tkn.type()) {
            case NULL -> JsonNull.INSTANCE;
            case TRUE -> JsonBoolean.TRUE;
            case FALSE -> JsonBoolean.FALSE;
            case STRING -> new JsonString(tkn.value());
            case NUMBER -> new JsonNumber(Double.parseDouble(tkn.value()));
            default -> throw new AssertionError("unexpected token " + tkn);
        };
    }

    private void parseException(String msg, Lexer.Coordinates coordinates) {
        //while(!stack.isEmpty()) System.err.println(stack.pop());
        throw new JsonParseException(msg, coordinates.row(), coordinates.column());
    }

    private Element next = null;

    @Override
    public boolean hasNext() {
        try {
            return (next = readNextElement()) != null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Element next() {
        var ret = next;
        next = null;
        return ret;
    }

    @Override
    public void close() throws IOException {
        lexer.close();
    }
}
