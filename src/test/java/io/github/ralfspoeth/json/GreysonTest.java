package io.github.ralfspoeth.json;

import io.github.ralfspoeth.json.io.JsonParseException;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class GreysonTest {

    private final PrintStream originalSystemOut = System.out;
    private ByteArrayOutputStream systemOutContent;

    @BeforeEach
    void setUpSystemOut() {
        systemOutContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(systemOutContent));
    }

    @AfterEach
    void restoreSystemOut() {
        System.setOut(originalSystemOut);
    }

    @Test
    void testReadFromString_validJson() {
        String jsonString = "{\"name\":\"test\",\"value\":123}";
        JsonValue element = Greyson.read(jsonString).orElseThrow();
        assertNotNull(element);
        assertInstanceOf(JsonObject.class, element);
        JsonObject jo = (JsonObject) element;
        assertEquals(new JsonString("test"), jo.members().get("name"));
        assertEquals(Basic.of(123), jo.members().get("value"));
    }

    @Test
    void testReadFromString_invalidJson() {
        String invalidJsonString = "{\"name\":\"test\",\"value\":123"; // Missing closing brace
        assertThrows(JsonParseException.class, () -> Greyson.read(invalidJsonString));
    }

    @Test
    void testReadFromString_nullInput() {
        assertThrows(NullPointerException.class, () -> Greyson.read((String) null));
    }

    @Test
    void testReadFromString_emptyString() {
        // Behavior for empty string depends on JsonReader.readElement(String) implementation
        // It might throw an exception or return null/JsonNull if it's considered valid empty content.
        // Assuming it throws an exception for non-JSON content.
        assertThrows(NoSuchElementException.class, () -> Greyson.readValue(""));
    }

    @Test
    void testReadFromReader_validJson() throws IOException {
        String jsonString = "[\"apple\", \"banana\"]";
        Reader reader = new StringReader(jsonString);
        JsonValue element = Greyson.read(reader).orElseThrow();
        assertNotNull(element);
        assertInstanceOf(JsonArray.class, element);
        JsonArray ja = (JsonArray) element;
        assertEquals(2, ja.elements().size());
        assertEquals(new JsonString("apple"), ja.elements().getFirst());
    }

    @Test
    void testReadFromReader_invalidJson() {
        String invalidJsonString = "[1, 2,"; // Missing closing bracket
        Reader reader = new StringReader(invalidJsonString);
        // Json.read(Reader) wraps JsonReader, which might throw during parsing.
        assertThrows(JsonParseException.class, () -> Greyson.read(reader));
    }

    @Test
    void testReadFromReader_nullInput() {
        // The Json.read(Reader) method creates `new JsonReader(rdr)`.
        // If rdr is null, JsonReader constructor should handle it (e.g., throw NPE).
        assertThrows(NullPointerException.class, () -> Greyson.read((Reader) null));
    }

    @Test
    void testReadFromReader_ioExceptionDuringRead() throws IOException {
        try (Reader faultyReader = new Reader() {
            @Override
            public int read(char @NonNull [] cbuf, int off, int len) throws IOException {
                throw new IOException("Simulated read error");
            }

            @Override
            public void close() {
            }
        }) {
            // Json.read(Reader) declares IOException
            assertThrows(IOException.class, () -> Greyson.read(faultyReader));
        }
    }

    @Test
    void testWrite_jsonArray() {
        // given
        JsonArray jsonArray = Builder.arrayBuilder()
                .add(JsonBoolean.TRUE)
                .add(JsonNull.INSTANCE)
                .build();
        // when
        var result = Greyson.write(new StringBuilder(), jsonArray).toString();
        String expectedOutput = "[true, null]";
        assertEquals(expectedOutput, result);
    }

    @Test
    void testWrite_jsonNull() throws IOException {
        StringWriter writer = new StringWriter();
        Greyson.write(writer, JsonNull.INSTANCE);
        assertEquals("null", writer.toString().trim());
    }

    @Test
    void testWriteToSystemOut() throws IOException {
        JsonObject jsonObject = Builder.objectBuilder()
                .put("message", new JsonString("hello"))
                .build();
        Greyson.writeToSystemOut(jsonObject);

        String expectedOutput = String.format("{%n    \"message\": \"hello\"%n}");
        // The systemOutContent will have an extra newline from PrintWriter
        assertEquals(expectedOutput, systemOutContent.toString().trim());
    }

    @Test
    void testWriteStringBuilder() {
        // given
        var sb = new StringBuilder();
        var jsonObject = Builder.objectBuilder()
                .put("message", new JsonString("hello"))
                .putBasic("num", 5)
                .build();
        // when
        var serial = Greyson.write(sb, jsonObject).toString();
        // then
        assertEquals(jsonObject, Greyson.read(serial).orElseThrow());
    }
}