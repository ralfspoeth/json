package com.pd.json.io;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;

public class Lexer implements AutoCloseable {

    public enum TokenType {
        NULL, TRUE, FALSE, STRING, NUMBER,
        OPENING_BRACE, CLOSING_BRACE,
        OPENING_BRACKET, CLOSING_BRACKET,
        COMMA, COLON
    }

    public record Token(TokenType type, String value) {
    }

    private final PushbackReader source;

    public Lexer(Reader source) {
        this.source = new PushbackReader(source);
    }

    private enum State {
        S0, SQUOTE, DQUOTE, LIT, EOF
    }

    private Token nextToken;
    private State state = State.S0;
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
        while (state != State.EOF && nextToken == null) {
            int r = source.read();
            if (r == -1) { // EOF
                switch (state) {
                    case LIT -> literal();
                    case SQUOTE, DQUOTE -> throw new IOException("unexpected end of file");
                }
                state = State.EOF;
            } else {
                char c = (char) r;
                switch (c) {
                    case '\'' -> {
                        if (state == State.SQUOTE) {
                            state = State.S0;
                            nextToken = new Token(TokenType.STRING, buffer.toString());
                            buffer.delete(0, buffer.capacity());
                        } else if(state == State.DQUOTE) {
                            buffer.append(c);
                        }
                        else {
                            state = State.SQUOTE;
                        }
                    }
                    case '\"' -> {
                        if (state == State.DQUOTE) {
                            state = State.S0;
                            nextToken = new Token(TokenType.STRING, buffer.toString());
                            buffer.delete(0, buffer.capacity());
                        } else if(state == State.SQUOTE) {
                            buffer.append(c);
                        }
                        else {
                            state = State.SQUOTE;
                        }
                    }
                    case ',', ':', '[', '{', ']', '}' -> {
                        switch(state) {
                            case S0 -> {
                                nextToken = switch (c) {
                                    case ',' -> new Token(TokenType.COMMA, ",");
                                    case ':' -> new Token(TokenType.COLON, ":");
                                    case '{' -> new Token(TokenType.OPENING_BRACE, "{");
                                    case '}' -> new Token(TokenType.CLOSING_BRACE, "}");
                                    case '[' -> new Token(TokenType.OPENING_BRACKET, "[");
                                    case ']' -> new Token(TokenType.CLOSING_BRACKET, "]");
                                    default -> throw new AssertionError("cannot happen");
                                };
                            }
                            case LIT -> {
                                literal();
                                source.unread(c);
                            }
                            case SQUOTE, DQUOTE -> buffer.append(c);
                            default -> throw new IOException("unexpected " + c + " after " + buffer);
                        }
                    }
                    default -> {
                        switch (state) {
                            case SQUOTE, DQUOTE -> buffer.append(c);
                            case S0 -> {
                                if(Character.isLetterOrDigit(c) || c=='-' || c=='.') {
                                    buffer.append(c);
                                    state = State.LIT;
                                } else if(!Character.isWhitespace(c)) {
                                    throw new IOException("Unexpected character " + c);
                                }
                            }
                            case LIT -> {
                                if(Character.isWhitespace(c)) {
                                    literal();
                                    state = State.S0;
                                }
                                else if(Character.isLetterOrDigit(c) || c=='-' || c=='.') {
                                    buffer.append(c);
                                }
                                else {
                                    throw new IOException("Unexpected character " + c);
                                }
                            }
                            default -> {
                                throw new IOException("Unexpected character " + c);
                            }
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
                var d = Double.parseDouble(text);
                yield new Token(TokenType.NUMBER, text);
            }
        };
        state = State.S0;
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
}
