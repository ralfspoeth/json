package io.github.ralfspoeth.json;

import org.junit.jupiter.api.Test;

import static java.util.Arrays.stream;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToStringTest {
    @Test
    void testToString() {
        var basics = new Basic<?>[]{JsonNull.INSTANCE, new JsonNumber(5), new JsonString("text")};
        assertAll(
                () -> assertTrue(stream(basics).allMatch(b -> b.toString().startsWith(b.getClass().getSimpleName() + "["))),
                () -> assertTrue(stream(basics).allMatch(b -> b.toString().endsWith("]"))),
                () -> assertTrue(stream(basics).allMatch(b -> b.toString().contains(String.valueOf(b.value()))))
        );
    }
}
