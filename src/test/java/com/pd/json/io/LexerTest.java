package com.pd.json.io;


import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class LexerTest {

    @Test
    void testNullLiteral() throws Exception {
        String source = "null";
        try (var lexer = new Lexer(new StringReader(source))) {
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.TokenType.NULL, lexer.next().type());
            assertFalse(lexer.hasNext());
        }
    }

    @Test
    void testTrueLiteral() throws Exception {
        String source = "true";
        try (var lexer = new Lexer(new StringReader(source))) {
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.TokenType.TRUE, lexer.next().type());
            assertFalse(lexer.hasNext());
        }
    }

    @Test
    void testFalseLiteral() throws Exception {
        String source = "false";
        try (var lexer = new Lexer(new StringReader(source))) {
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.TokenType.FALSE, lexer.next().type());
            assertFalse(lexer.hasNext());
        }
    }

    @Test
    void testNullFalseTrueLiterals() throws Exception {
        // note that this is not legal json syntax
        String source = "true false null";
        try(var lexer = new Lexer(new StringReader(source))) {
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.TokenType.TRUE, lexer.next().type());
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.TokenType.FALSE, lexer.next().type());
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.TokenType.NULL, lexer.next().type());
            assertFalse(lexer.hasNext());
        }
    }

    @Test
    void testNullFalseTrueLiteralsWithMoreWS() throws Exception {
        // note that this is not legal json syntax
        String source = """
            true    false
            
            
            null""";
        try(var lexer = new Lexer(new StringReader(source))) {
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.TokenType.TRUE, lexer.next().type());
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.TokenType.FALSE, lexer.next().type());
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.TokenType.NULL, lexer.next().type());
            assertFalse(lexer.hasNext());
        }
    }

    @Test
    void testEmptyObject() throws Exception{
        var source = "{}";
        try(var lexer = new Lexer(new StringReader(source))) {
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.TokenType.OPENING_BRACE, lexer.next().type());
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.TokenType.CLOSING_BRACE, lexer.next().type());
            assertFalse(lexer.hasNext());
        }
    }
    @Test
    void testEmptyObjectWithWS() throws Exception{
        var source = """
                   {
                  ,
                  :
                 }
                """;
        try(var lexer = new Lexer(new StringReader(source))) {
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.TokenType.OPENING_BRACE, lexer.next().type());
            assertTrue(lexer.hasNext());
            lexer.next(); // comma
            assertTrue(lexer.hasNext());
            lexer.next(); // colon
            assertTrue(lexer.hasNext());
            assertEquals(Lexer.TokenType.CLOSING_BRACE, lexer.next().type());
            assertFalse(lexer.hasNext());
        }
    }

    @Test
    void testParseLarge() throws Exception {
        try(var src = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/large-file.json")),
                StandardCharsets.UTF_8
        )); var lxr = new Lexer(src))
        {
            var tmp = new ArrayList<Lexer.Token>();
            while(lxr.hasNext()) {
                tmp.add(lxr.next());
            }
            assertAll(
                    () -> assertFalse(tmp.contains(null))
            );
        }
    }
}