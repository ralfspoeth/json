package io.github.ralfspoeth.json.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static java.util.stream.StreamSupport.stream;

class Lexer implements AutoCloseable {

    private static final Set<Integer> LIT_CHARS = "0123456789nulltrefaseE-+."
            .codePoints()
            .boxed()
            .collect(toSet());

    enum Type {
        // literal types
        STRING, NUMBER,
        // constants
        NULL, TRUE, FALSE,
        // all other single character token types
        OPENING_BRACE, CLOSING_BRACE, // braces
        OPENING_BRACKET, CLOSING_BRACKET, // brackets
        COMMA, COLON // , :
    }

    sealed interface Token permits LiteralToken, FixToken {
        String value();
        Type type();
    }

    record LiteralToken(Type type, String value) implements Token {
        private static final Set<Type> LITERAL_TYPES = Set.of(Type.STRING, Type.NUMBER);
        LiteralToken {
            if(!LITERAL_TYPES.contains(type)) {
                throw new IllegalArgumentException(type + " must be one of " + LITERAL_TYPES);
            }
        }
    }

    enum FixToken implements Token {
        COMMA(Type.COMMA, ","),
        NULL(Type.NULL, "null"),
        TRUE (Type.TRUE, "true"),
        FALSE(Type.FALSE, "false"),
        COLON(Type.COLON, ":"),
        OPENING_BRACE(Type.OPENING_BRACE, "{"),
        CLOSING_BRACE(Type.CLOSING_BRACE, "}"),
        OPENING_BRACKET(Type.OPENING_BRACKET, "["),
        CLOSING_BRACKET(Type.CLOSING_BRACKET, "]");

        private final Type type;
        private final String value;

        FixToken(Type type, String value) {
            this.value = value;
            this.type = type;
        }

        @Override
        public Type type() {
            return type;
        }
        @Override
        public String value() {
            return value;
        }
    }

    private final PushbackReader source;

    Lexer(Reader source) {
        this.source = source instanceof PushbackReader pr ? pr :
                source instanceof BufferedReader br ? new PushbackReader(br) :
                        new PushbackReader(new BufferedReader(source));
    }

    static Stream<Token> tokenStream(Reader rdr) {
        return stream(Spliterators.spliteratorUnknownSize(new Iterator<>() {
            private final Lexer lxr = new Lexer(rdr);

            @Override
            public boolean hasNext() {
                var next = false;
                try {
                    next = lxr.hasNext();
                    if (!next) {
                        try {
                            lxr.close();
                        } catch (IOException closeEx) {
                            // swallow
                        }
                    }
                } catch (IOException ioex) {
                    try {
                        lxr.close();
                    } catch (IOException closeEx) {
                        // swallow
                    }
                }
                return next;
            }

            @Override
            public Token next() {
                return lxr.next();
            }
        }, Spliterator.IMMUTABLE | Spliterator.ORDERED), false);
    }

    private enum State {
        INITIAL, DQUOTE, LIT, EOF
    }

    private Token nextToken;
    private State state = State.INITIAL;
    private final StringBuilder buffer = new StringBuilder();
    private final CharBuffer unicodeSequence = CharBuffer.allocate(4);

    boolean hasNext() throws IOException {
        readNextToken();
        return nextToken != null;
    }

    Token next() {
        if (nextToken == null) {
            throw new IllegalStateException("Lexer has no next token");
        } else {
            var tmp = nextToken;
            nextToken = null;
            return tmp;
        }
    }

    private void readNextToken() throws IOException {
        boolean escaped = false;
        boolean uc = false;
        while (state != State.EOF && nextToken == null) {
            int r = source.read();
            if (r == -1) { // EOF
                switch (state) {
                    case LIT -> literal();
                    case DQUOTE -> parseException("unexpected end of file");
                }
                state = State.EOF;
            } else {
                char c = (char) r;
                if (c == '\n') {
                    row++;
                    column = 1;
                } else {
                    column++;
                }
                if (escaped) {
                    var nc = switch (c) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'u' -> {
                            unicodeSequence.rewind();
                            uc = true;
                            yield '\u0000';
                        }
                        case '\"' -> '\"';
                        case '\\' -> '\\';
                        case '/' -> '/';
                        default -> {
                            if(r>0x001F) parseException("escaped non-control character " + c);
                            yield c;
                        }
                    };
                    escaped = false;
                    if (state == State.DQUOTE) {
                        if(nc!='\u0000') buffer.append(nc);
                    } else {
                        unexpectedCharacter(c);
                    }
                } else if(uc) {
                    unicodeSequence.put(c);
                    if (unicodeSequence.position() == unicodeSequence.capacity()) {
                        uc = false;
                        char[] chars = new char[unicodeSequence.capacity()];
                        unicodeSequence.flip().get(chars);
                        var value = Integer.parseInt(String.valueOf(chars), 16);
                        buffer.appendCodePoint(value);
                    }
                }
                else switch (c) {
                    case '\'' -> {
                        if (state == State.DQUOTE) {
                            buffer.append(c);
                        } else {
                            unexpectedCharacter(c);
                        }
                    }
                    case '\"' -> {
                        if (state == State.DQUOTE) {
                            state = State.INITIAL;
                            nextToken = new LiteralToken(Type.STRING, buffer.toString());
                            buffer.setLength(0);
                        } else {
                            state = State.DQUOTE;
                        }
                    }
                    case '\\' -> {
                        if (state == State.DQUOTE) { // within string
                            escaped = true;
                        } else {
                            unexpectedCharacter(c);
                        }
                    }
                    case ',', ':', '[', '{', ']', '}' -> {
                        switch (state) {
                            case INITIAL -> nextToken = switch (c) {
                                case ',' -> FixToken.COMMA;
                                case ':' -> FixToken.COLON;
                                case '{' -> FixToken.OPENING_BRACE;
                                case '}' -> FixToken.CLOSING_BRACE;
                                case '[' -> FixToken.OPENING_BRACKET;
                                case ']' -> FixToken.CLOSING_BRACKET;
                                default -> throw new AssertionError();
                            };
                            case LIT -> {
                                literal();
                                source.unread(c);
                            }
                            case DQUOTE -> buffer.append(c);
                            default -> parseException("unexpected " + c + " after " + buffer);
                        }
                    }
                    default -> {
                        switch (state) {
                            case DQUOTE -> {
                                if(r <= 0x001F) {
                                    parseException("Unescaped control character: " + c);
                                }
                                buffer.append(c);
                            }
                            case INITIAL -> {
                                if (LIT_CHARS.contains(r)) {
                                    buffer.append(c);
                                    state = State.LIT;
                                } else if (!Character.isWhitespace(c)) {
                                    unexpectedCharacter(c);
                                }
                            }
                            case LIT -> {
                                if (Character.isWhitespace(c)) {
                                    literal();
                                    state = State.INITIAL;
                                } else if (LIT_CHARS.contains(r)) {
                                    buffer.append(c);
                                } else {
                                    unexpectedCharacter(c);
                                }
                            }
                            default -> unexpectedCharacter(c);
                        }
                    }
                }
            }
        }
    }

    private void unexpectedCharacter(char c) throws JsonParseException {
        parseException("Unexpected character '" + c + "'");
    }

    private static final Pattern JSON_NUMBER = Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    private static boolean jsonNumber(String s) {
        return JSON_NUMBER.matcher(s).matches();
    }

    private void literal() {
        var text = buffer.toString();
        buffer.delete(0, buffer.capacity());
        nextToken = switch (text) {
            case "null" -> FixToken.NULL;
            case "true" -> FixToken.TRUE;
            case "false" -> FixToken.FALSE;
            default -> jsonNumber(text)
                    ? new LiteralToken(Type.NUMBER, text)
                    : parseException("cannot parse %s as double".formatted(text));
        };
        state = State.INITIAL;
    }

    private Token parseException(String message) throws JsonParseException {
        throw new JsonParseException(message, row, column);
    }

    private int row = 1, column = 1;

    record Coordinates(int row, int column) {}

    Coordinates coordinates() {
        return new Coordinates(row, column);
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
}