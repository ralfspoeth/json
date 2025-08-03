package io.github.ralfspoeth.json.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
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

    private final Reader source;

    private int ch;
    private boolean readCh;

    private int read() throws IOException {
        if (readCh) {
            readCh = false;
            return ch;
        } else {
            return source.read();
        }
    }

    private void unread(int ch) {
        this.ch = ch;
        readCh = true;
    }

    Lexer(Reader rdr) {
        this.source = switch (rdr) {
            case StringReader sr -> sr;
            case BufferedReader br -> br;
            default -> new BufferedReader(rdr);
        };
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
    // the buffer
    private char[] buffer = new char[1_024];
    private int bufferPos = 0;

    private void append(char c) {
        if (bufferPos == buffer.length) {
            char[] tmp = new char[buffer.length * 2];
            System.arraycopy(buffer, 0, tmp, 0, buffer.length);
            buffer = tmp;
        }
        buffer[bufferPos++] = c;
    }

    private void appendCodePoint(int codePoint) {
        for(char c: Character.toChars(codePoint)) {
            append(c);
        }
    }

    private String litBuffer() {
        var ret = new String(buffer, 0, bufferPos);
        bufferPos = 0;
        return ret;
    }

    // unicode seq
    private final char[] unicSeq = new char[4];
    private int unicSeqPos = 0;

    private void unicSeqClear() {
        unicSeqPos = 0;
    }

    private void unicSeqAdd(char c) {
        unicSeq[unicSeqPos++] = c;
    }

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
            int r = read();
            // special case -1 (EOF)
            if (r == -1) {
                state = switch (state) {
                    case NUM_LIT, CONST_LIT -> literal();
                    case STRLIT, STRLIT_ESC, STRLIT_UC ->
                            throw new JsonParseException("unexpected end of file in string literal " + Arrays.toString(buffer), row, column);
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
                            append(c);
                            yield State.NUM_LIT;
                        }
                        case '\"' -> State.STRLIT;
                        case 'n', 't', 'f' -> {
                            append(c);
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
                        case ' ', '\t', '\r', '\n' -> State.INITIAL;
                        default -> throw new JsonParseException("Unexpected character: " + c, row, column);


                    };

                    case NUM_LIT -> switch (c) {
                        case '.', 'e', 'E', '+', '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                            append(c);
                            yield State.NUM_LIT;
                        }
                        case 'n', 't', 'f', 'u', 'r', 'a', 'l', 's', ',', ':', '}', ']', '\"', '{', '[' -> {
                            unread(c);
                            yield literal();
                        }
                        case ' ', '\t', '\r', '\n' -> literal();
                        default ->
                                throw new JsonParseException("Unexpected character: " + c + " in number literal", row, column);
                    };

                    case CONST_LIT -> switch (c) {
                        case 'a', 'l', 's', 'e', 't', 'r', 'u' -> {
                            append(c);
                            yield State.CONST_LIT;
                        }
                        case ',', '}', ']', '\"', '{', '[', '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                            unread(c);
                            yield literal();
                        }
                        case ' ', '\t', '\r', '\n' -> literal();
                        default -> throw new JsonParseException("Misspelled literal: " + c, row, column);
                    };

                    case STRLIT -> switch (c) {
                        case '\"' -> stringLiteral();
                        case '\\' -> State.STRLIT_ESC;
                        default -> {
                            if (r <= 0x001F)
                                throw new JsonParseException("Unexpected control character " + c, row, column);
                            else {
                                append(c);
                                yield State.STRLIT;
                            }
                        }
                    };

                    case STRLIT_ESC -> switch (c) {
                        case 'u' -> {
                            unicSeqClear();
                            yield State.STRLIT_UC;
                        }
                        case 'n' -> {
                            append('\n');
                            yield State.STRLIT;
                        }
                        case 'r' -> {
                            append('\r');
                            yield State.STRLIT;
                        }
                        case 't' -> {
                            append('\t');
                            yield State.STRLIT;
                        }
                        case '\\' -> {
                            append('\\');
                            yield State.STRLIT;
                        }
                        case '"' -> {
                            append('\"');
                            yield State.STRLIT;
                        }
                        case 'f' -> {
                            append('\f');
                            yield State.STRLIT;
                        }
                        case '/' -> {
                            append('/');
                            yield State.STRLIT;
                        }
                        case 'b' -> {
                            append('\b');
                            yield State.STRLIT;
                        }
                        case '\t', '\r', '\n', ' ', '\f' ->
                                throw new JsonParseException("control character after escape " + c, row, column);
                        default -> {
                            if (Character.isLetterOrDigit(c) || r > 0x001F || r == 0) {
                                throw new JsonParseException("escaped non-control character " + c, row, column);
                            }
                            append(c);
                            yield State.STRLIT;
                        }
                    };

                    case STRLIT_UC -> switch (c) {
                        case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'A', 'B',
                             'C', 'D', 'E', 'F' -> {
                            unicSeqAdd(c);
                            if (unicSeqPos == 4) {
                                int value = Integer.parseInt(String.valueOf(unicSeq), 16);
                                unicSeqPos = 0;
                                appendCodePoint(value);
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
        var text = litBuffer();
        nextToken = new LiteralToken(Type.STRING, text);
        return State.INITIAL;
    }

    private Lexer.State literal() {
        var text = litBuffer();
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

    int row() {
        return row;
    }

    int column() {
        return column;
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
}