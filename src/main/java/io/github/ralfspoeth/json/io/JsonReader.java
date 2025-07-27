package io.github.ralfspoeth.json.io;

import io.github.ralfspoeth.basix.coll.Stack;
import io.github.ralfspoeth.json.*;
import io.github.ralfspoeth.json.Aggregate.JsonArrayBuilder;
import io.github.ralfspoeth.json.Aggregate.JsonObjectBuilder;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Optional;

import static io.github.ralfspoeth.json.io.JsonReader.Elem.Char.colon;
import static io.github.ralfspoeth.json.io.JsonReader.Elem.Char.comma;
import static io.github.ralfspoeth.json.io.Lexer.FixToken.*;
import static io.github.ralfspoeth.json.io.Lexer.Type.STRING;

/**
 * Instances parse character streams into JSON {@link JsonValue}s.
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
public class JsonReader implements AutoCloseable {

    private final Lexer lexer;

    /**
     * Instantiate this class on top of a {@link Reader}.
     * The source will be closed together with {@code this}.
     *
     * @param src the character source
     */
    public JsonReader(Reader src) {
        this(src, false);
    }

    private JsonReader(Reader src, boolean piped) {
        this.lexer = new Lexer(src);
        this.usedInStreamPipeline = piped;
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

        record NameElem(String name) implements Elem {}

        enum Char implements Elem {colon, comma}

        record Root(JsonValue elem) implements Elem {
        }
    }

    private final Stack<Elem> stack = new Stack<>();

    /**
     * Reads the first and only JSON element from the source.
     *
     * @return the JSON element
     * @throws IOException whenever the underlying source throws
     */
    public JsonValue readElement() throws IOException {
        return read().orElseThrow(
                () -> new JsonParseException("No JSON element in the source",
                        lexer.coordinates().row(),
                        lexer.coordinates().column()
                )
        );
    }

    /**
     * Reads the first JSON element if there is one.
     *
     * @return a JSON value wrapped in an Optional
     * @throws IOException whenever the lexer throws
     */
    public Optional<JsonValue> read() throws IOException {
        var result = readNextElement();
        if (lexer.hasNext()) {
            throw new JsonParseException("Input contains tokens after the first element", lexer.coordinates().row(), lexer.coordinates().column());
        } else {
            return Optional.ofNullable(result);
        }
    }

    private JsonValue readNextElement() throws IOException {
        // repeat to take the next token while the lexer has more tokens available
        // and either the stack is empty or,
        // in case we expect to read more than one JSON element from the potentially unbounded source,
        // the top element is not a root element
        while (lexer.hasNext() && (stack.isEmpty() || !stack.top().getClass().equals(Elem.Root.class))) {
            var tkn = lexer.next();
            // we switch over the type of the token as the primary level compound state
            switch (tkn) {
                // a colon is acceptable if and only if the current element at the
                // top of the stack is a name-value-pair
                case COLON -> {
                    if (stack.top() instanceof Elem.NameElem) {
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
                        case null, default -> parseException("unexpected token: " + tkn, lexer.coordinates());
                    }
                }
                // opening braces (as well as opening brackets, see below)
                // start json aggregate which an appear wherever a value may appear,
                // that is, at the start, in an empty array, and after a colon or a comma.
                case OPENING_BRACE -> {
                    switch (stack.top()) {
                        case null -> stack.push(Elem.ObjBuilderElem.empty());
                        case Elem.ArrBuilderElem(var builder)
                                when builder.isEmpty() -> stack.push(Elem.ObjBuilderElem.empty());
                        case Elem.Char ignored -> stack.push(Elem.ObjBuilderElem.empty());
                        default -> parseException("unexpected token " + tkn.value(), lexer.coordinates());
                    }
                }
                // opens an array, otherwise like a brace
                case OPENING_BRACKET -> {
                    switch (stack.top()) {
                        case null -> stack.push(Elem.ArrBuilderElem.empty());
                        case Elem.Char ignored -> stack.push(Elem.ArrBuilderElem.empty());
                        case Elem.ArrBuilderElem(var builder) when builder.isEmpty() ->
                                stack.push(Elem.ArrBuilderElem.empty());
                        default -> parseException("unexpected token " + tkn.value(), lexer.coordinates());
                    }
                }
                // closes a json object
                // there should be an object builder on top of the stack
                case CLOSING_BRACE -> {
                    var obj = switch (stack.top()) {
                        case Elem.ObjBuilderElem obe -> {
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
                // closing a JSON array
                // the array builder should be on top of the stack
                case CLOSING_BRACKET -> {
                    if (stack.top() instanceof Elem.ArrBuilderElem(var builder)) {
                        stack.pop();
                        var jsonArray = builder.build();
                        handle(tkn.value(), jsonArray);
                    } else {
                        parseException("unexpected token: " + tkn, lexer.coordinates());
                    }
                }
                // literal tokens including null, true, false, number, string
                // where string is a special case because it can be the name part of a name-value-pair
                case Lexer.LiteralToken(var type, var val) -> {
                    if (type == STRING &&
                            stack.top() instanceof Elem.ObjBuilderElem(var builder) && builder.isEmpty()
                    ) {
                        stack.push(new Elem.NameElem(val));
                    } else if (type == STRING && comma.equals(stack.top())) {
                        stack.pop();
                        switch (stack.top()) {
                            case Elem.ObjBuilderElem ignored -> stack.push(new Elem.NameElem(val));
                            case Elem.ArrBuilderElem abe -> abe.builder.item(new JsonString(val));
                            case null, default -> parseException("Unexpected value: " + val, lexer.coordinates());
                        }
                    } else {
                        var literalToken = token2Value(tkn);
                        handle(val, literalToken);
                    }
                }
            }
        }

        // an empty stack is okay, signaling
        // that the input stream contains nothing but whitespace
        if (stack.isEmpty()) {
            return null;
        } else if (stack.pop() instanceof Elem.Root(var elem)) {
            // standard case: stack contains a single element at its top
            return elem;
        } else {
            // otherwise, something went wrong
            parseException("stack not empty or top-most element not a JsonElement", lexer.coordinates());
            return null;
        }
    }

    // handle tokens UNLESS these are element names
    // in a JSON object
    private void handle(String token, JsonValue v) {
        switch (stack.top()) {
            // stack is empty
            case null -> stack.push(new Elem.Root(v));
            // colon or comma at the top
            case Elem.Char nc -> {
                switch (nc) {
                    // colon: name-value-pair is second on top
                    case colon -> {
                        // pop colon
                        stack.pop();
                        // the topmost element must be an NVP with a null element,
                        // and the next stack element must be an object builder
                        if (!stack.isEmpty()
                                && stack.pop() instanceof Elem.NameElem(String name)
                                && stack.top() instanceof Elem.ObjBuilderElem(var builder)) {
                            // add name-value-pair to the object builder
                            builder.named(name, v);
                        } else {
                            parseException("unexpected token: " + token, lexer.coordinates());
                        } // in every other case, something went wrong
                    }
                    // comma on top
                    case comma -> {
                        stack.pop(); // pop comma
                        if (stack.top() instanceof Elem.ArrBuilderElem(var builder)) {
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
        return switch (tkn) {
            case Lexer.LiteralToken(var type, var val) -> switch (type) {
                case NULL -> JsonNull.INSTANCE;
                case TRUE -> JsonBoolean.TRUE;
                case FALSE -> JsonBoolean.FALSE;
                case STRING -> new JsonString(val);
                case NUMBER -> new JsonNumber(new BigDecimal(val));
            };
            case Lexer.FixToken ignored -> throw new AssertionError();
        };
    }

    private void parseException(String msg, Lexer.Coordinates coordinates) {
        //while(!stack.isEmpty()) System.err.println(stack.pop());
        throw new JsonParseException(msg, coordinates.row(), coordinates.column());
    }

    private static class ElementIterator implements Iterator<JsonValue> {

        private final JsonReader jr;
        private JsonValue next = null;

        private ElementIterator(JsonReader jr) {
            this.jr = jr;
        }

        @Override
        public boolean hasNext() {
            try {
                return (next = jr.readNextElement()) != null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public JsonValue next() {
            var ret = next;
            next = null;
            return ret;
        }
    }

    private final boolean usedInStreamPipeline;

    @Override
    public void close() throws IOException {
        if (!usedInStreamPipeline) lexer.close();
    }
}
