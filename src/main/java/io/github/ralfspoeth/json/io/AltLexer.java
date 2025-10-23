package io.github.ralfspoeth.json.io;

import java.io.IOException;
import java.io.Reader;

class AltLexer implements AutoCloseable {

    private static final int COMMA = ',';
    private static final int COLON = ':';
    private static final int OPENING_BRACE = '{';
    private static final int CLOSING_BRACE = '}';
    private static final int OPENING_BRACKET = '[';
    private static final int CLOSING_BRACKET = ']';
    private static final int STRING = '"';
    private static final int NUMBER = '0';
    private static final int LITERAL = '_';

    private final Reader rdr;

    AltLexer(Reader rdr) {
        this.rdr = rdr;
    }

    // internal state
    // the buffer
    private static final int KILO = 1_024;
    private char[] buffer = new char[2 * KILO];
    private int bufferPos = 0;
    private int tokenStart = 0;
    private int endOfFile = -1; // set after end of file has been reached

    private void readNextChars() throws IOException {
        if (bufferPos > KILO)  {
            System.arraycopy(buffer, bufferPos, buffer, 0, buffer.length - bufferPos);
            bufferPos = 0;
        }
        int read = rdr.read(buffer, bufferPos, 1_024);
    }

    // current state;
    private int state = 0; // 0: initial

    private void readNextToken() {

    }

    @Override
    public void close() throws IOException {
        rdr.close();
    }
}
