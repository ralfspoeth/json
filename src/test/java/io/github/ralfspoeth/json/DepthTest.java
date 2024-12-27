package io.github.ralfspoeth.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DepthTest {

    @Test
    void depthBasics() {
        // given s: stream of basic instances
        List<Element> basics = List.of(
                JsonNull.INSTANCE,
                JsonBoolean.TRUE,
                JsonBoolean.FALSE,
                JsonNumber.ZERO,
                new JsonString("")
        );
        List<Element> empties = List.of(
                new JsonObject(Map.of()), new JsonArray(List.of())
        );
        // then
        assertAll(
                () -> assertTrue(basics.stream().allMatch(e->e.depth()==1)),
                () -> assertTrue(empties.stream().allMatch(e->e.depth()==1)),
                () -> assertEquals(2, new JsonArray(basics).depth()),
                () -> assertEquals(2, new JsonObject(Map.of("a", JsonBoolean.TRUE)).depth()),
                () -> assertEquals(2, new JsonObject(Map.of("b", new JsonArray(List.of()))).depth()),
                () -> assertEquals(2, new JsonObject(Map.of("c", new JsonObject(Map.of()))).depth()),
                () -> assertEquals(3, new JsonObject(Map.of("d", new JsonArray(List.of(JsonBoolean.FALSE)))).depth())
        );
    }

}
