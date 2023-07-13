package com.github.ralfspoeth.json.io;

import java.io.*;

class Lexer implements AutoCloseable {

    enum TokenType {
        NULL, TRUE, FALSE, STRING, NUMBER,
        OPENING_BRACE, CLOSING_BRACE,
        OPENING_BRACKET, CLOSING_BRACKET,
        COMMA, COLON
    }

    record Token(TokenType type, String value) {
    }

    private final PushbackReader source;

    Lexer(Reader source) {
        this.source = source instanceof PushbackReader pr ? pr :
                source instanceof BufferedReader br ? new PushbackReader(br) :
                        new PushbackReader(new BufferedReader(source));
    }

    private enum State {
        INITIAL, DQUOTE, LIT, EOF
    }

    private Token nextToken;
    private State state = State.INITIAL;
    private final StringBuilder buffer = new StringBuilder();

    public boolean hasNext() throws IOException {
        readNextToken();
        return nextToken != null;
    }

    public Token next() {
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
                        ioex("Illegal character sequence \\" + c);
                    }
                } else switch (c) {
                    case '\'' -> {
                        if (state == State.DQUOTE) {
                            buffer.append(c);
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
                            ioex("unexpected character \\");
                        }
                    }
                    case ',', ':', '[', '{', ']', '}' -> {
                        switch (state) {
                            case INITIAL -> nextToken = switch (c) {
                                case ',' -> new Token(TokenType.COMMA, ",");
                                case ':' -> new Token(TokenType.COLON, ":");
                                case '{' -> new Token(TokenType.OPENING_BRACE, "{");
                                case '}' -> new Token(TokenType.CLOSING_BRACE, "}");
                                case '[' -> new Token(TokenType.OPENING_BRACKET, "[");
                                case ']' -> new Token(TokenType.CLOSING_BRACKET, "]");
                                default -> throw new AssertionError("cannot happen");
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
                            case DQUOTE -> buffer.append(c);
                            case INITIAL -> {
                                if (Character.isLetterOrDigit(c) || c == '-' || c == '.') {
                                    buffer.append(c);
                                    state = State.LIT;
                                } else if (!Character.isWhitespace(c)) {
                                    ioex("Unexpected character " + c);
                                }
                            }
                            case LIT -> {
                                if (Character.isWhitespace(c)) {
                                    literal();
                                    state = State.INITIAL;
                                } else if (Character.isLetterOrDigit(c) || c == '-' || c == '.') {
                                    buffer.append(c);
                                } else {
                                    ioex("Unexpected character " + c);
                                }
                            }
                            default -> ioex("Unexpected character " + c);
                        }
                    }
                }
            }
        }
    }

    private void literal() {
        var text = buffer.toString();
        buffer.delete(0, buffer.capacity());
        nextToken = switch (text) {
            case "null" -> new Token(TokenType.NULL, text);
            case "true" -> new Token(TokenType.TRUE, text);
            case "false" -> new Token(TokenType.FALSE, text);
            default -> {
                double ignored = Double.parseDouble(text);
                yield new Token(TokenType.NUMBER, text);
            }
        };
        state = State.INITIAL;
    }

    private void ioex(String message) throws IOException {
        throw new IOException("%s at row: %d, col: %d".formatted(message, row, column));
    }

    private int row = 1, column = 1;

    record Coordinates(int row, int column) {
    }

    public Coordinates coordinates() {
        return new Coordinates(row, column);
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
}