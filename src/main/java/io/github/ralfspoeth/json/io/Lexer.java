package io.github.ralfspoeth.json.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;

class Lexer implements AutoCloseable {

    enum TokenType {
        NULL, TRUE, FALSE, STRING, NUMBER,
        OPENING_BRACE, CLOSING_BRACE,
        OPENING_BRACKET, CLOSING_BRACKET,
        COMMA, COLON
    }

    record Token(TokenType type, String value) {
        final static Token COMMA = new Token(TokenType.COMMA, ",");
        final static Token NULL = new Token(TokenType.NULL, "null");
        final static Token TRUE = new Token(TokenType.TRUE, "true");
        final static Token FALSE = new Token(TokenType.FALSE, "false");
        final static Token COLON = new Token(TokenType.COLON, ":");
        final static Token OPENING_BRACE = new Token(TokenType.OPENING_BRACE, "{");
        final static Token CLOSING_BRACE = new Token(TokenType.CLOSING_BRACE, "}");
        final static Token OPENING_BRACKET = new Token(TokenType.OPENING_BRACKET, "[");
        final static Token CLOSING_BRACKET = new Token(TokenType.CLOSING_BRACKET, "]");
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
        while (state != State.EOF && nextToken == null) {
            int r = source.read();
            if (r == -1) { // EOF
                switch (state) {
                    case LIT -> literal();
                    case DQUOTE -> ioex("unexpected end of file");
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
                        default -> c;
                    };
                    escaped = false;
                    if (state == State.DQUOTE) {
                        buffer.append(nc);
                    } else {
                        unexpectedCharacter(c);
                    }
                } else switch (c) {
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
                            nextToken = new Token(TokenType.STRING, buffer.toString());
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
                                case ',' -> Token.COMMA;
                                case ':' -> Token.COLON;
                                case '{' -> Token.OPENING_BRACE;
                                case '}' -> Token.CLOSING_BRACE;
                                case '[' -> Token.OPENING_BRACKET;
                                case ']' -> Token.CLOSING_BRACKET;
                                default -> throw new AssertionError();
                            };
                            case LIT -> {
                                literal();
                                source.unread(c);
                            }
                            case DQUOTE -> buffer.append(c);
                            default -> ioex("unexpected " + c + " after " + buffer);
                        }
                    }
                    default -> {
                        switch (state) {
                            case DQUOTE -> {
                                if(Character.isISOControl(r)) {
                                    ioex("Unescaped control character: " + c);
                                }
                                buffer.append(c);
                            }
                            case INITIAL -> {
                                if (Character.isLetterOrDigit(c) || c == '-' || c == '.') {
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
                                } else if (Character.isLetterOrDigit(c) || c == '-' || c == '.' || c=='e' || c=='E') {
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

    private void unexpectedCharacter(char c) throws IOException {
        ioex("Unexpected character '" + c + "'");
    }

    private static final Pattern JSON_NUMBER = Pattern.compile("-?(?:0|(?:[1-9]|[0-9]*))(?:\\.[0-9]+)?(?:[eE]?[-+]?[0-9]+)?");

    private static boolean jsonNumber(String s) {
        return JSON_NUMBER.matcher(s).matches();
    }


    private void literal() throws IOException {
        var text = buffer.toString();
        buffer.delete(0, buffer.capacity());
        nextToken = switch (text) {
            case "null" -> Token.NULL;
            case "true" -> Token.TRUE;
            case "false" -> Token.FALSE;
            default -> jsonNumber(text)
                    ? new Token(TokenType.NUMBER, text)
                    : ioex("cannot parse %s as double".formatted(text));

        };
        state = State.INITIAL;
    }

    private Token ioex(String message) throws IOException {
        throw new IOException("%s at row: %d, col: %d".formatted(message, row, column));
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