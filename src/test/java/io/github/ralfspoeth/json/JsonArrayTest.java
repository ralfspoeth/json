package io.github.ralfspoeth.json;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class JsonArrayTest {

    @Test
    void testImmutable() {
        var ja = new JsonArray(new ArrayList<>());
        assertAll(
                () ->assertThrows(UnsupportedOperationException.class, () -> ja.elements().add(JsonNull.INSTANCE))
        );
    }

    @Test
    void testNoNulls() {
        List<Element> elems = new ArrayList<>();
        elems.add(null);
        assertThrows(NullPointerException.class, () -> new JsonArray(elems));
    }

    @Test
    void testIntFunction() {
        List<Element> elems = List.of(JsonNull.INSTANCE, JsonBoolean.TRUE, new JsonNumber(5d));
        var ja = new JsonArray(elems);
        assertAll(
                () -> assertEquals(elems.size(), ja.elements().size()),
                () -> assertEquals(elems, IntStream.range(0, elems.size()).mapToObj(ja).toList())
        );
    }

}
