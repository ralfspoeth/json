package io.github.ralfspoeth.json.io;

import io.github.ralfspoeth.basix.coll.Stack;
import io.github.ralfspoeth.json.data.*;
import io.github.ralfspoeth.json.data.Builder.JsonArrayBuilder;
import io.github.ralfspoeth.json.data.Builder.JsonObjectBuilder;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.Optional;

import static io.github.ralfspoeth.json.io.JsonReader.Elem.ArrBuilderElem.arrBuilderElem;
import static io.github.ralfspoeth.json.io.JsonReader.Elem.ObjBuilderElem.objBuilderElem;
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
public class JsonReader implements Closeable {

    private static final class FastUtf8Reader extends Reader {
        private static final byte[] DFA_TABLE = {
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
                8, 8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 10, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 3, 3, 11, 6, 6, 6, 5, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
                0, 12, 24, 36, 60, 96, 84, 12, 12, 12, 48, 72, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 0, 12, 12, 12, 12, 12, 0, 12, 0, 12, 12,
                12, 24, 12, 12, 12, 12, 12, 24, 12, 24, 12, 12, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12,
                12, 12, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12, 12, 36, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12, 12, 36, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12
        };

        // source stream
        private final InputStream in;

        // byte buffer cache
        private final byte[] byteBuf = new byte[8_192];
        private int byteBufPtr = 0, byteBufLen = 0;
        // char buffer cache
        private final char[] charBuf = new char[8_192];
        private int charBufPtr = 0, charBufLen = 0;

        // current state
        private int state = 0;
        private int codePoint = 0;

        /**
         * Constructor
         * @param in the source stream
         */
        public FastUtf8Reader(InputStream in) {
            this.in = in;
        }

        /**
         * Implementation of single-character read.
         * Uses charBuf to avoid decoding logic on every single call.
         */
        @Override
        public int read() throws IOException {
            if (charBufPtr >= charBufLen) {
                fillCharBuffer();
                if (charBufLen == -1) return -1;
            }
            return charBuf[charBufPtr++];
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            if (len == 0) return 0;

            int totalCharsWritten = 0;

            // 1. First, drain anything left in the internal char cache
            if (charBufPtr < charBufLen) {
                int available = charBufLen - charBufPtr;
                int toCopy = Math.min(available, len);
                System.arraycopy(charBuf, charBufPtr, cbuf, off, toCopy);
                charBufPtr += toCopy;
                totalCharsWritten += toCopy;
                if (totalCharsWritten == len) return totalCharsWritten;
            }

            // 2. If we need more than what was cached, decode directly into user's buffer
            // This avoids double-copying for large read requests.
            int remaining = len - totalCharsWritten;
            int decoded = decodeToBuffer(cbuf, off + totalCharsWritten, remaining);

            if (decoded == -1) {
                return (totalCharsWritten == 0) ? -1 : totalCharsWritten;
            }

            return totalCharsWritten + decoded;
        }

        private void fillCharBuffer() throws IOException {
            charBufPtr = 0;
            charBufLen = decodeToBuffer(charBuf, 0, charBuf.length);
        }

        /**
         * Core decoding engine: transforms bytes into chars into a target array.
         */
        private int decodeToBuffer(char[] target, int off, int len) throws IOException {
            int charsDecoded = 0;

            while (charsDecoded < len) {
                if (byteBufPtr >= byteBufLen) {
                    byteBufLen = in.read(byteBuf);
                    byteBufPtr = 0;
                    if (byteBufLen == -1) {
                        return (charsDecoded == 0) ? -1 : charsDecoded;
                    }
                }

                while (byteBufPtr < byteBufLen && charsDecoded < len) {
                    int b = byteBuf[byteBufPtr++] & 0xFF;
                    int type = DFA_TABLE[b];

                    codePoint = (state == 0) ? (0xFF >> type) & b : (b & 0x3F) | (codePoint << 6);
                    state = DFA_TABLE[256 + state + type];

                    if (state == 0) {
                        if (codePoint <= 0xFFFF) {
                            target[off + charsDecoded++] = (char) codePoint;
                        } else {
                            // Handle surrogate pairs
                            target[off + charsDecoded++] = Character.highSurrogate(codePoint);
                            if (charsDecoded < len) {
                                target[off + charsDecoded++] = Character.lowSurrogate(codePoint);
                            } else {
                                /* * Edge case: No room for low surrogate in target.
                                 * We must push it back or cache it. For simplicity in this specialized
                                 * reader, we always use charBuf when calling decodeToBuffer from read(),
                                 * ensuring there's always room (charBuf is large).
                                 */
                                throw new IOException("Buffer overflow during surrogate decoding");
                            }
                        }
                    } else if (state == 12) {
                        throw new IOException("Malformed UTF-8 sequence.");
                    }
                }
            }
            return charsDecoded;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

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

    /**
     * Instantiate a JsonReader utilizing a super fast input stream
     * decoder.
     * @param in the input stream
     */
    public JsonReader(InputStream in) {
        this(new FastUtf8Reader(in));
    }

    sealed interface Elem {
        record ObjBuilderElem(JsonObjectBuilder builder) implements Elem {
            static ObjBuilderElem objBuilderElem() {
                return new ObjBuilderElem(Builder.objectBuilder());
            }
        }

        record ArrBuilderElem(JsonArrayBuilder builder) implements Elem {
            static ArrBuilderElem arrBuilderElem() {
                return new ArrBuilderElem(Builder.arrayBuilder());
            }
        }

        record NameElem(String name) implements Elem {}

        enum Char implements Elem {colon, comma}

        record Root(JsonValue elem) implements Elem {
        }
    }

    // return to the stack from basix
    private final Stack<Elem> stack = new Stack<>();

    /**
     * Reads the first and only JSON element from the source.
     *
     * @return the JSON element
     * @throws IOException whenever the underlying source throws
     */
    public JsonValue readValue() throws IOException {
        return read().orElseThrow(
                () -> new JsonParseException("No JSON element in the source",
                        lexer.row(),
                        lexer.column()
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
            throw new JsonParseException("Input contains tokens after the first element", lexer.row(), lexer.column());
        } else {
            return Optional.ofNullable(result);
        }
    }

    private @Nullable JsonValue readNextElement() throws IOException {
        // repeat to take the next token while the lexer has more tokens available.
        // and either the stack is empty or,
        // in case we expect to read more than one JSON element from the potentially unbounded source,
        // the top element is not a root element.
        while (lexer.hasNext() && !(stack.top() instanceof Elem.Root)) {
            var tkn = lexer.next();
            // we switch over the type of the token as the primary level compound state
            switch (tkn) {
                // a colon is acceptable if and only if the current element at the
                // top of the stack is a name-value-pair
                case COLON -> {
                    if (stack.top() instanceof Elem.NameElem) {
                        stack.push(Elem.Char.colon);
                    } else {
                        parseEx("unexpected token: " + tkn);
                    }
                }
                // a comma separates elements in an aggregate
                // that is, the top of the stack must be a non-empty
                // aggregate element
                case COMMA -> {
                    switch (stack.top()) {
                        case Elem.ArrBuilderElem abe when !abe.builder.isEmpty() -> stack.push(Elem.Char.comma);
                        case Elem.ObjBuilderElem obe when !obe.builder.isEmpty() -> stack.push(Elem.Char.comma);
                        case null, default -> parseEx("unexpected token: " + tkn);
                    }
                }
                // opening braces (as well as opening brackets, see below)
                // that is, at the start, in an empty array, and after a colon or a comma.
                case OPENING_BRACE -> {
                    switch (stack.top()) {
                        case null -> stack.push(objBuilderElem());
                        case Elem.ArrBuilderElem(var builder)
                                when builder.isEmpty() -> stack.push(objBuilderElem());
                        case Elem.Char ignored -> stack.push(objBuilderElem());
                        default -> parseEx("unexpected token " + tkn.value());
                    }
                }
                // opens an array, otherwise like a brace
                case OPENING_BRACKET -> {
                    switch (stack.top()) {
                        case null -> stack.push(arrBuilderElem());
                        case Elem.Char ignored -> stack.push(arrBuilderElem());
                        case Elem.ArrBuilderElem(var builder) when builder.isEmpty() -> stack.push(arrBuilderElem());
                        default -> parseEx("unexpected token " + tkn.value());
                    }
                }
                // closes a JSON object
                // there should be an object builder on top of the stack
                case CLOSING_BRACE -> {
                    var obj = switch (stack.top()) {
                        case Elem.ObjBuilderElem obe -> {
                            stack.pop();
                            yield obe.builder.build();
                        }
                        case null, default -> {
                            parseEx("unexpected token: " + tkn);
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
                        parseEx("unexpected token: " + tkn);
                    }
                }
                // literal tokens including null, true, false, number, string
                // where string is a special case because it can be the name part of a name-value-pair
                case Lexer.LiteralToken(var type, var val) -> {
                    if (type == STRING &&
                            stack.top() instanceof Elem.ObjBuilderElem(var builder) && builder.isEmpty()
                    ) {
                        stack.push(new Elem.NameElem(val));
                    } else if (type == STRING && Elem.Char.comma.equals(stack.top())) {
                        stack.pop();
                        switch (stack.top()) {
                            case Elem.ObjBuilderElem ignored -> stack.push(new Elem.NameElem(val));
                            case Elem.ArrBuilderElem abe -> abe.builder.add(new JsonString(val));
                            case null, default -> parseEx("Unexpected value: " + val);
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
            parseEx("stack not empty or top-most element not a JsonElement");
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
                            builder.put(name, v);
                        } else {
                            parseEx("unexpected token: " + token);
                        } // in every other case, something went wrong
                    }
                    // comma on top
                    case comma -> {
                        stack.pop(); // pop comma
                        if (stack.top() instanceof Elem.ArrBuilderElem(var builder)) {
                            builder.add(v);
                        } else {
                            parseEx("unexpected token: " + token);
                        }
                    }
                }
            }
            case Elem.ArrBuilderElem(var builder) when builder.isEmpty() -> builder.add(v);
            default -> parseEx("unexpected token: " + token);
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

    private void parseEx(String msg) {
        throw new JsonParseException(msg, lexer.row(), lexer.column());
    }

    @Override
    public void close() throws IOException {
        lexer.close();
    }
}
