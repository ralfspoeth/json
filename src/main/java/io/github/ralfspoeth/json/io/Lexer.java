package io.github.ralfspoeth.json.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;

class Lexer implements AutoCloseable {

    sealed interface Token permits LiteralToken, FixToken {
        String value();
    }

    enum Type {
        STRING, NUMBER, NULL, TRUE, FALSE
    }

    record LiteralToken(Type type, String value) implements Token {}

    enum FixToken implements Token {
        COMMA(","),
        COLON(":"),
        OPENING_BRACE("{"),
        CLOSING_BRACE("}"),
        OPENING_BRACKET("["),
        CLOSING_BRACKET("]");

        private final String value;

        FixToken(String value) {
            this.value = value;
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
        INITIAL,
        STRLIT, STRLIT_ESC, STRLIT_UC,
        NUM_LIT, CONST_LIT,
        EOF
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
        while (state != State.EOF && nextToken == null) {
            int r = source.read();
            // special case -1 (EOF)
            if (r == -1) {
                state = switch (state) {
                    case NUM_LIT, CONST_LIT -> literal();
                    case STRLIT, STRLIT_ESC, STRLIT_UC ->
                            throw new JsonParseException("unexpected end of file in string literal " + buffer, row, column);
                    case INITIAL, EOF -> State.EOF;
                };
            } else {
                // char value for codepoint
                char c = (char) r;
                // row/column accounting
                if (c == '\n') {
                    row++;
                    column = 1;
                } else {
                    column++;
                }
                state = switch (state) {
                    case INITIAL -> switch (c) {
                        case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                            buffer.append(c);
                            yield State.NUM_LIT;
                        }
                        case '\"' -> State.STRLIT;
                        case 'n', 't', 'f' -> {
                            buffer.append(c);
                            yield State.CONST_LIT;
                        }
                        case '{', '}', '[', ']', ':', ',' -> {
                            nextToken = switch (c) {
                                case '{' -> FixToken.OPENING_BRACE;
                                case '}' -> FixToken.CLOSING_BRACE;
                                case '[' -> FixToken.OPENING_BRACKET;
                                case ']' -> FixToken.CLOSING_BRACKET;
                                case ':' -> FixToken.COLON;
                                case ',' -> FixToken.COMMA;
                                default -> throw new AssertionError();
                            };
                            yield State.INITIAL;
                        }
                        case ' ', '\t', '\r', '\n' ->  State.INITIAL;
                        default -> throw new JsonParseException("Unexpected character: " + c, row, column);


                    };

                    case NUM_LIT -> switch (c) {
                        case '.', 'e', 'E', '+', '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                            buffer.append(c);
                            yield State.NUM_LIT;
                        }
                        case 'n', 't', 'f', 'u', 'r', 'a', 'l', 's', ',', ':', '}', ']', '\"', '{', '[' -> {
                            source.unread(c);
                            yield literal();
                        }
                        case ' ', '\t', '\r', '\n' -> literal();
                        default -> throw new JsonParseException("Unexpected character: " + c + " in number literal", row, column);
                    };

                    case CONST_LIT -> switch (c) {
                        case 'a', 'l', 's', 'e', 't', 'r', 'u' -> {
                            buffer.append(c);
                            yield State.CONST_LIT;
                        }
                        case ',', '}', ']', '\"', '{', '[', '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                            source.unread(c);
                            yield literal();
                        }
                        case ' ', '\t', '\r', '\n' -> literal();
                        default -> throw new JsonParseException("Misspelled literal: " + buffer.append(c), row, column);
                    };

                    case STRLIT -> switch (c) {
                        case '\"' -> stringLiteral();
                        case '\\' -> State.STRLIT_ESC;
                        default -> {
                            if (r <= 0x001F)
                                throw new JsonParseException("Unexpected control character " + c, row, column);
                            else {
                                buffer.append(c);
                                yield State.STRLIT;
                            }
                        }
                    };

                    case STRLIT_ESC -> switch (c) {
                        case 'u' -> {
                            unicodeSequence.clear();
                            yield State.STRLIT_UC;
                        }
                        case 'n' -> {
                            buffer.append('\n');
                            yield State.STRLIT;
                        }
                        case 'r' -> {
                            buffer.append('\r');
                            yield State.STRLIT;
                        }
                        case 't' -> {
                            buffer.append('\t');
                            yield State.STRLIT;
                        }
                        case '\\' -> {
                            buffer.append('\\');
                            yield State.STRLIT;
                        }
                        case '"' -> {
                            buffer.append('\"');
                            yield State.STRLIT;
                        }
                        case 'f' -> {
                            buffer.append('\f');
                            yield State.STRLIT;
                        }
                        case '/' -> {
                            buffer.append('/');
                            yield State.STRLIT;
                        }
                        case 'b' -> {
                            buffer.append('\b');
                            yield State.STRLIT;
                        }
                        case '\t', '\r', '\n', ' ', '\f' ->
                                throw new JsonParseException("control character after escape " + c, row, column);
                        default -> {
                            if (r > 0x001F) {
                                throw new JsonParseException("escaped non-control character " + c, row, column);
                            }
                            buffer.append(c);
                            yield State.STRLIT;
                        }
                    };

                    case STRLIT_UC -> switch (c) {
                        case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'A', 'B',
                             'C', 'D', 'E', 'F' -> {
                            unicodeSequence.put(c);

                            if (unicodeSequence.position() == unicodeSequence.capacity()) {
                                char[] chars = new char[unicodeSequence.capacity()];
                                unicodeSequence.flip().get(chars);
                                int value = Integer.parseInt(String.valueOf(chars), 16);
                                if(value > 0) {
                                    buffer.appendCodePoint(value);
                                } else {
                                    throw new JsonParseException("illegal unicode sequence " + unicodeSequence.flip(), row, column);
                                }
                                yield State.STRLIT;
                            } else {
                                yield State.STRLIT_UC;
                            }
                        }
                        default ->
                                throw new JsonParseException("Unexpected character " + c + " in unicode sequence", row, column);
                    };
                    case EOF -> throw new JsonParseException("character " + c + " after end of file", row, column);
                };
            }
        }
    }

    private static final Pattern JSON_NUMBER = Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    private static boolean jsonNumber(String s) {
        return JSON_NUMBER.matcher(s).matches();
    }

    private Lexer.State stringLiteral() {
        var text = buffer.toString();
        buffer.delete(0, buffer.capacity());
        nextToken = new LiteralToken(Type.STRING, text);
        return State.INITIAL;
    }

    private Lexer.State literal() {
        var text = buffer.toString();
        buffer.delete(0, buffer.capacity());
        nextToken = switch (text) {
            case "null" -> new LiteralToken(Type.NULL, text);
            case "true" -> new LiteralToken(Type.TRUE, text);
            case "false" -> new LiteralToken(Type.FALSE, text);
            default -> {
                if (jsonNumber(text)) yield new LiteralToken(Type.NUMBER, text);
                else throw new JsonParseException("cannot parse %s as double".formatted(text), row, column);
            }
        };
        return State.INITIAL;
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