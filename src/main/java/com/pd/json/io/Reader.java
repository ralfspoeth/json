package com.pd.json.io;

import com.pd.json.data.JsonElement;
import com.pd.json.data.JsonString;
import com.pd.json.data.JsonValue;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

public class Reader {

    private enum State {
        S0, EOF, OBJ, ARR, LIT, NUM, STR, ESC
    }

    public JsonElement fromJson(java.io.Reader source) throws IOException {
        var state = State.S0;
        var buffer = new StringBuilder();
        var stack = new ArrayDeque<Object>();
        do {
            int codePoint = source.read();
            if (codePoint == -1) { // EOF, end-of-file
                switch (state) {
                    case S0:
                        state = State.EOF;
                        break;
                    case LIT, NUM:
                        if (stack.isEmpty()) {
                            return JsonValue.of(buffer.toString());
                        } else {
                            throw new IllegalStateException("geht do nich");
                        }
                }
            } else {
                char ch = (char) codePoint;
                if (Character.isWhitespace(ch)) {
                    if (state == State.STR) {
                        buffer.append(ch);
                    }
                } else {
                    switch (ch) {
                        case '\\':
                            switch (state) {
                                case STR:
                                    state = State.ESC;
                                    break;
                                case ESC:
                                    buffer.append('\\');
                                    state = State.STR;
                                    break;
                                default:
                                    throw new IllegalStateException("\\ outside string");
                            }
                        case '"':
                            switch (state) {
                                case ESC:
                                    buffer.append(ch);
                                    break; // ignore " if it has been escaped
                                case S0:
                                    stack.push(JsonString.of(""));
                                    state = State.STR;
                                    break;
                                case STR:
                                    var old = stack.pop();
                                    assert old.equals(JsonString.of(""));
                                    stack.push(JsonString.of(buffer.toString()));
                                    state = State.S0;
                                    break;
                                default:
                                    throw new IllegalStateException("\" in wrong place");
                            }
                            ;
                        case '{':
                            switch (state) {
                                case S0:
                                    stack.push(Map.of());
                                    state = State.OBJ;
                                    break;
                                case STR:
                                    buffer.append(ch);
                                    break;
                                default:
                                    throw new IllegalStateException("{ in wrong place");
                            }
                        case '}':
                            switch (state) {
                                case OBJ:

                            }
                    }
                }
            }
        } while (state!=State.EOF);
        return unwind(stack);
    }

    private JsonElement unwind(Deque<?> stack) {
        return null;
    }

}
