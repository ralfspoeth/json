package io.github.ralfspoeth.json.io;


import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class LexerTest {


    @Test
    void testuc() throws IOException {
        var source = new StringBuilder()
                .append('"')
                .append('\\')
                .append('u')
                .append('a')
                .append('b')
                .append('c')
                .append('d')
                .append('"')
                .toString();
        try (var lexer = new Lexer(new StringReader(source))) {
            assertTrue(lexer.hasNext());
            var token = lexer.next();
            assertEquals(Lexer.Type.STRING, token.type());
            assertEquals("\uabcd", token.value());
        }
    }
    @Test
    void testNullLiteral() throws Exception {
        String source = "null";
        try (var lexer = new Lexer(new StringReader(source))) {
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.Type.NULL, lexer.next().type());
            assertFalse(lexer.hasNext());
        }
    }

    @Test
    void testTrueLiteral() throws Exception {
        String source = "true";
        try (var lexer = new Lexer(new StringReader(source))) {
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.Type.TRUE, lexer.next().type());
            assertFalse(lexer.hasNext());
        }
    }

    @Test
    void testFalseLiteral() throws Exception {
        String source = "false";
        try (var lexer = new Lexer(new StringReader(source))) {
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.Type.FALSE, lexer.next().type());
            assertFalse(lexer.hasNext());
        }
    }

    @Test
    void testNullFalseTrueLiterals() throws Exception {
        // note that this is not legal json syntax
        String source = "true false null";
        try (var lexer = new Lexer(new StringReader(source))) {
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.Type.TRUE, lexer.next().type());
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.Type.FALSE, lexer.next().type());
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.Type.NULL, lexer.next().type());
            assertFalse(lexer.hasNext());
        }
    }

    @Test
    void testNullFalseTrueLiteralsWithMoreWS() throws Exception {
        // note that this is not legal json syntax
        String source = """
                true    false
                
                
                null""";
        try (var lexer = new Lexer(new StringReader(source))) {
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.Type.TRUE, lexer.next().type());
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.Type.FALSE, lexer.next().type());
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.Type.NULL, lexer.next().type());
            assertFalse(lexer.hasNext());
        }
    }

    @Test
    void testEmptyObject() throws Exception {
        var source = "{}";
        try (var lexer = new Lexer(new StringReader(source))) {
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.Type.OPENING_BRACE, lexer.next().type());
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.Type.CLOSING_BRACE, lexer.next().type());
            assertFalse(lexer.hasNext());
        }
    }

    @Test
    void testEmptyObjectWithWS() throws Exception {
        var source = """
                   {
                  ,
                  :
                 }
                """;
        try (var lexer = new Lexer(new StringReader(source))) {
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.Type.OPENING_BRACE, lexer.next().type());
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.Type.COMMA, lexer.next().type());
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.Type.COLON, lexer.next().type());
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.Type.CLOSING_BRACE, lexer.next().type());
            assertFalse(lexer.hasNext());
        }
    }

    @Test
    void testIllegalSQuote() throws Exception {
        try (var lexer = new Lexer(new StringReader("'"))) {
            assertThrows(JsonParseException.class, lexer::hasNext);
        }
    }

    @Test
    void testParseLarge() throws Exception {
        try (var src = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/large-file.json")),
                StandardCharsets.UTF_8
        )); var lxr = new Lexer(src)) {
            var tmp = new ArrayList<Lexer.Token>();
            while (lxr.hasNext()) {
                tmp.add(lxr.next());
            }
            var grp = tmp.stream().collect(Collectors.groupingBy(Lexer.Token::type, Collectors.counting()));
            assertAll(
                    () -> assertFalse(tmp.contains(null)),
                    () -> assertTrue(grp.keySet().containsAll(EnumSet.allOf(Lexer.Type.class)))
            );
        }
    }

    @Test
    void testTwo() throws IOException {
        var list = new ArrayList<Lexer.Token>();
        try (var lexer = new Lexer(new StringReader("1[2]"))) {
            while (lexer.hasNext()) {
                list.add(lexer.next());
            }
        }
        assertAll(
                () -> assertEquals(4, list.size()),
                () -> assertEquals("1", list.getFirst().value()),
                () -> assertEquals("2", list.get(2).value())
        );
    }
}