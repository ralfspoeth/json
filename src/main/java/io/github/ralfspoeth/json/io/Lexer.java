package io.github.ralfspoeth.json.io;

import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.regex.Pattern;

class Lexer implements AutoCloseable {

    sealed interface Token permits LiteralToken, FixToken {
        String value();
    }

    enum Type {
        STRING, NUMBER, NULL, TRUE, FALSE
    }

    value record LiteralToken(Type type, String value) implements Token {}

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

    static final class InternalPushbackReader implements AutoCloseable {

        private final Reader source;

        InternalPushbackReader(Reader source) {this.source = source;}

        // hand-rolled pushback facility
        private int ch = -1;

        int read() throws IOException {
            // if a character has been unread before, return it
            if (ch!=-1) {
                var ret = ch;
                ch = -1;
                return ret;
            }
            // otherwise, read from the source
            else {
                return source.read();
            }
        }

        void unread(int ch) {
            this.ch = ch;
        }

        @Override
        public void close() throws IOException {
            source.close();
        }
    }

    private final InternalPushbackReader source;

    Lexer(Reader rdr) {
        this.source = new InternalPushbackReader(switch (rdr) {
            case StringReader sr -> sr;
            case BufferedReader br -> br;
            default -> new BufferedReader(rdr); // buffer in any case
        });
    }

    private enum State {
        INITIAL,
        STRLIT, STRLIT_ESC, STRLIT_UC,
        NUM_LIT, CONST_LIT,
        EOF
    }

    // the next token
    private @Nullable Token nextToken;
    // the current state
    private State state = State.INITIAL;

    // very similar to a string builder
    static final class Buffer {
        // the buffered char array
        private char[] buffer = new char[4_096];
        // the position where to add the next char read
        private int bufferPos = 0;

        void append(char c) {
            // double buffer size
            if (bufferPos == buffer.length) {
                char[] tmp = new char[buffer.length * 2];
                System.arraycopy(buffer, 0, tmp, 0, buffer.length);
                buffer = tmp;
            }
            // add the char
            buffer[bufferPos++] = c;
        }

        void appendCodePoint(int codePoint) {
            for (char c : Character.toChars(codePoint)) {
                append(c);
            }
        }

        String contents() {
            var ret = String.valueOf(buffer, 0, bufferPos);
            bufferPos = 0;
            return ret;
        }
    }

    // buffer for string an const literals
    private final Buffer buffer = new Buffer();

    // intermediate unicode sequence
    static final class UnicodeSequence {
        private final char[] unicSeq = new char[4];
        private int unicSeqPos = 0;

        void add(char c) {
            unicSeq[unicSeqPos++] = c;
        }

        boolean isFull() {
            return unicSeqPos == 4;
        }

        int toCodePoint() {
            unicSeqPos = 0;
            return Integer.parseInt(String.valueOf(unicSeq), 16);
        }
    }

    // unicode sequence
    private final UnicodeSequence unicSeq = new UnicodeSequence();


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
                            throw new JsonParseException("unexpected end of file in string literal " + Arrays.toString(buffer.buffer), row, column);
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
                        // white space
                        case ' ', '\t', '\r', '\n' -> State.INITIAL;
                        // start of numbers
                        case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> appendAndState(c, State.NUM_LIT);
                        // start of strings
                        case '\"' -> State.STRLIT;
                        // start of const literals
                        case 'n', 't', 'f' -> appendAndState(c, State.CONST_LIT);
                        // separator char
                        case '{', '}', '[', ']', ':', ',' -> fixToken(c);
                        // else error
                        default -> parseException("Unexpected character: " + c);
                    };

                    case NUM_LIT -> switch (c) {
                        // append num char
                        case '.', 'e', 'E', '+', '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> append(c);
                        //  unexpected char
                        case 'n', 't', 'f', 'u', 'r', 'a', 'l', 's', ',', ':', '}', ']', '\"', '{', '[' -> unread(c);
                        // separator
                        case ' ', '\t', '\r', '\n' -> literal();
                        default -> parseException("Unexpected character: " + c + " in number literal");
                    };

                    case CONST_LIT -> switch (c) {
                        case 'a', 'l', 's', 'e', 't', 'r', 'u' -> append(c);
                        case ',', '}', ']', '\"', '{', '[', '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> unread(c);
                        case ' ', '\t', '\r', '\n' -> literal();
                        default -> parseException("Misspelled literal: " + c);
                    };

                    case STRLIT -> switch (c) {
                        case '\"' -> stringLiteral();
                        case '\\' -> State.STRLIT_ESC;
                        default -> (r <= 0x001F) ? parseException("Unexpected control character " + c) : append(c);
                    };

                    case STRLIT_ESC -> switch (c) {
                        case 'u' -> State.STRLIT_UC;
                        case 'n' -> appendAndState('\n', State.STRLIT);
                        case 'r' -> appendAndState('\r', State.STRLIT);
                        case 't' -> appendAndState('\t', State.STRLIT);
                        case '\\' -> appendAndState('\\', State.STRLIT);
                        case '"' -> appendAndState('\"', State.STRLIT);
                        case 'f' -> appendAndState('\f', State.STRLIT);
                        case '/' -> appendAndState('/', State.STRLIT);
                        case 'b' -> appendAndState('\b', State.STRLIT);
                        case '\t', '\r', '\n', ' ', '\f' -> parseException("control character after escape " + c);
                        default -> (Character.isLetterOrDigit(c) || r > 0x001F || r == 0)
                                ? parseException("escaped non-control character " + c)
                                : appendAndState(c, State.STRLIT);
                    };

                    case STRLIT_UC -> switch (c) {
                        case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'A', 'B',
                             'C', 'D', 'E', 'F' -> {
                            unicSeq.add(c);
                            if (unicSeq.isFull()) {
                                buffer.appendCodePoint(unicSeq.toCodePoint());
                                yield State.STRLIT;
                            } else {
                                yield State.STRLIT_UC;
                            }
                        }
                        default -> parseException("Unexpected character " + c + " in unicode sequence");
                    };

                    case EOF -> parseException("character " + c + " after end of file");
                };
            }
        }
    }

    private State unread(char c) {
        source.unread(c);
        return literal();
    }

    private State fixToken(char c) {
        nextToken = switch (c) {
            case '{' -> FixToken.OPENING_BRACE;
            case '}' -> FixToken.CLOSING_BRACE;
            case '[' -> FixToken.OPENING_BRACKET;
            case ']' -> FixToken.CLOSING_BRACKET;
            case ':' -> FixToken.COLON;
            case ',' -> FixToken.COMMA;
            default -> throw new AssertionError();
        };
        return State.INITIAL;
    }

    private State stringLiteral() {
        nextToken = new LiteralToken(Type.STRING, buffer.contents());
        return State.INITIAL;
    }

    private State literal() {
        var text = buffer.contents();
        nextToken = switch (text) {
            case "null" -> new LiteralToken(Type.NULL, text);
            case "true" -> new LiteralToken(Type.TRUE, text);
            case "false" -> new LiteralToken(Type.FALSE, text);
            default -> numLiteral(text);
        };
        return State.INITIAL;
    }

    private State appendAndState(char c, Lexer.State s) {
        buffer.append(c);
        return s;
    }

    private State append(char c) {
        buffer.append(c);
        return state;
    }

    private State parseException(String msg) {
        throw new JsonParseException(msg, row, column);
    }

    private Token numLiteral(String s) {
        if (jsonNumber(s))
            return new LiteralToken(Type.NUMBER, s);
        else
            throw new JsonParseException("cannot parse %s as double".formatted(s), row, column);
    }

    // parse numbers
    private static final Pattern JSON_NUMBER = Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    private static boolean jsonNumber(String s) {
        return JSON_NUMBER.matcher(s).matches();
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