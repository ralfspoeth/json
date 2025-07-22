package io.github.ralfspoeth.json;

import io.github.ralfspoeth.json.io.JsonParseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        JsonValue element = Greyson.read(jsonString);
        assertNotNull(element);
        assertInstanceOf(JsonObject.class, element);
        JsonObject jo = (JsonObject) element;
        assertEquals(new JsonString("test"), jo.members().get("name"));
        assertEquals(new JsonNumber(123), jo.members().get("value"));
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
        assertThrows(JsonParseException.class, () -> Greyson.read(""));
    }

    @Test
    void testReadFromReader_validJson() throws IOException {
        String jsonString = "[\"apple\", \"banana\"]";
        Reader reader = new StringReader(jsonString);
        JsonValue element = Greyson.read(reader);
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
            public int read(char[] cbuf, int off, int len) throws IOException {
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
        StringWriter writer = new StringWriter();
        JsonArray jsonArray = Aggregate.arrayBuilder()
                .item(JsonBoolean.TRUE)
                .item(JsonNull.INSTANCE)
                .build();
        Greyson.write(writer, jsonArray);
        // Default writer for arrays might be a single line
        String expectedOutput = "[true, null]";
        assertEquals(expectedOutput, writer.toString().trim());
    }

    @Test
    void testWrite_jsonNull() {
        StringWriter writer = new StringWriter();
        Greyson.write(writer, JsonNull.INSTANCE);
        assertEquals("null", writer.toString().trim());
    }

    @Test
    void testWriteToSystemOut() {
        JsonObject jsonObject = Aggregate.objectBuilder()
                .named("message", new JsonString("hello"))
                .build();
        Greyson.writeToSystemOut(jsonObject);

        String expectedOutput = String.format("{%n    \"message\": \"hello\"%n}");
        // The systemOutContent will have an extra newline from PrintWriter
        assertEquals(expectedOutput, systemOutContent.toString().trim());
    }

    @Test
    void testStream_validMultipleElements() throws IOException {
        // Assuming JsonReader as an Iterator can read multiple top-level JSON values
        // separated by whitespace.
        String multiJsonString = "{\"id\":1} \"test_string\" [1,2,3] null true 42.5";
        Reader reader = new StringReader(multiJsonString);

        List<JsonValue> elements = Greyson.stream(reader).collect(Collectors.toList());

        assertNotNull(elements);
        assertEquals(6, elements.size());

        assertInstanceOf(JsonObject.class, elements.get(0));
        assertEquals(new JsonNumber(1), ((JsonObject)elements.get(0)).members().get("id"));

        assertEquals(new JsonString("test_string"), elements.get(1));

        assertInstanceOf(JsonArray.class, elements.get(2));
        assertEquals(3, ((JsonArray)elements.get(2)).elements().size());

        assertEquals(JsonNull.INSTANCE, elements.get(3));
        assertEquals(JsonBoolean.TRUE, elements.get(4));
        assertEquals(new JsonNumber(42.5), elements.get(5));
    }

    @Test
    void testStream_emptyReader() throws IOException {
        Reader reader = new StringReader("");
        List<JsonValue> elements = Greyson.stream(reader).toList();
        assertTrue(elements.isEmpty());
    }

    @Test
    void testStream_readerWithOnlyWhitespace() throws IOException {
        Reader reader = new StringReader("   \n \t  ");
        List<JsonValue> elements = Greyson.stream(reader).toList();
        assertTrue(elements.isEmpty());
    }

    @Test
    void testStream_ioExceptionDuringStream() throws IOException {
        try (Reader faultyReader = new Reader() {
            private boolean firstRead = true;

            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                if (firstRead) { // Allow initial read for JsonReader setup if any
                    if (len > 0) {
                        cbuf[off] = '{'; // Start of a JSON object
                        firstRead = false;
                        return 1;
                    }
                    return -1;
                }
                throw new IOException("Simulated stream read error");
            }

            @Override
            public void close() {
            }
        }) {
            // The IOException should propagate from the stream's spliterator
            assertThrows(RuntimeException.class, () -> {
                try (Stream<JsonValue> stream = Greyson.stream(faultyReader)) {
                    stream.toList();
                }
            });
        }
    }

    @Test
    void testStream_parsingErrorInStream() {
        String faultyJsonStream = "{\"key\": \"value\"} [1,2"; // Valid first, then invalid
        Reader reader = new StringReader(faultyJsonStream);

        // How parsing errors are handled in stream depends on JsonReader's iterator behavior.
        // It might throw an unchecked exception when .next() is called on the invalid part.
        assertThrows(JsonParseException.class, () -> {
            try (Stream<JsonValue> stream = Greyson.stream(reader)) {
                stream.toList(); // Consumption triggers parsing
            }
        });
    }
}