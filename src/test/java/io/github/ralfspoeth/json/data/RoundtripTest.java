package io.github.ralfspoeth.json.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundtripTest {

    @Test
    void roundTripBoolean() {
        // given
        var jbf = JsonBoolean.FALSE;
        var jbt = JsonBoolean.TRUE;
        // then
        assertAll(
                () -> assertEquals(jbf, jbf.builder().build()),
                () -> assertEquals(jbt, jbt.builder().build())
        );
    }

    @Test
    void roundTripNum() {
        // given
        var nums = List.of(
                Basic.of(1),
                Basic.of(2),
                Basic.of(5)
        );
        // then
        nums.forEach(n -> assertEquals(n, n.builder().build()));
    }

    @Test
    void roundTripString() {
        // given
        var strs = List.of(
                Basic.of("hallo"),
                Basic.of("welt")
        );
        // then
        strs.forEach(s -> assertEquals(s, s.builder().build()));
    }

    @Test
    void roundTripNull() {
        // given
        var n = JsonNull.INSTANCE;
        // then
        assertEquals(n, n.builder().build());
    }

    @Test
    void roundTripArray() {
        // given
        var a = new JsonArray(List.of(Basic.of(1), JsonNull.INSTANCE, JsonBoolean.FALSE, Basic.of("hallo")));
        // then
        assertEquals(a, a.builder().build());
    }

    @Test
    void roundTripObject() {
        // given
        var o = new JsonObject(Map.of("a", Basic.of(1), "b", JsonNull.INSTANCE, "c", JsonBoolean.FALSE, "d", Basic.of("hallo")));
        // then
        assertEquals(o, o.builder().build());
    }
}
