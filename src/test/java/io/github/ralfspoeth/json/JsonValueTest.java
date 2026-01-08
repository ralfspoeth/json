package io.github.ralfspoeth.json;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.Builder.arrayBuilder;
import static io.github.ralfspoeth.json.Builder.objectBuilder;
import static org.junit.jupiter.api.Assertions.*;

class JsonValueTest {

    @Test
    void testDepth() {
        assertAll(
                () -> assertEquals(1, objectBuilder().build().depth()),
                () -> assertEquals(2, objectBuilder().put("x", JsonNull.INSTANCE).build().depth()),
                () -> assertEquals(3, objectBuilder().put("x", objectBuilder().put("y", JsonBoolean.TRUE).build()).build().depth())
        );
    }

    @Test
    void testMutability() {
        var je = new JsonArray(
                List.of(
                        JsonNull.INSTANCE, JsonBoolean.FALSE, JsonBoolean.TRUE,
                        Basic.of(5), new JsonString("five"),
                        new JsonObject(Map.of("a", JsonBoolean.TRUE, "b", new JsonObject(Map.of())))
                )
        );
        var obj = je.elements().stream()
                .filter(e -> e instanceof JsonObject)
                .map(JsonObject.class::cast)
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertThrows(UnsupportedOperationException.class, () -> je.elements().add(JsonNull.INSTANCE)),
                () -> assertThrows(UnsupportedOperationException.class, () -> je.elements().removeFirst()),
                () -> assertThrows(UnsupportedOperationException.class, () -> obj.members().put("c", JsonBoolean.TRUE))

        );
    }

    @Test
    void testSwitch() {
        var str = Stream.of(
                JsonBoolean.FALSE,
                JsonBoolean.TRUE,
                JsonNumber.ZERO,
                JsonNull.INSTANCE,
                new JsonString("str"),
                arrayBuilder()
                        .add(JsonNumber.ZERO)
                        .build(),
                objectBuilder()
                        .put("a", JsonBoolean.TRUE)
                        .put("b", arrayBuilder())
                        .build()
        );
        var l = str.map(e -> switch (e) {
            case Basic<?> b -> b.json();
            case Aggregate a -> a.toString();
        }).toList();
        assertNotNull(l);
    }

    @Test
    void testDecimalValue() {
        assertAll(
                () -> assertEquals(0, JsonValue.of(10).decimalValue().orElseThrow().compareTo(BigDecimal.TEN)),
                () -> assertEquals(BigDecimal.TWO, JsonValue.of(null).decimalValue(BigDecimal.TWO)),
                () -> assertThrows(NoSuchElementException.class, () -> Basic.of(null).decimalValue().orElseThrow())
        );
    }

    @Test
    void testStringValue() {
        assertAll(
                () -> assertEquals("hello", JsonValue.of("hello").stringValue().orElseThrow()),
                () -> assertEquals("hello", JsonValue.of(null).stringValue("hello")),
                () -> assertThrows(NoSuchElementException.class, () -> Basic.of(null).stringValue().orElseThrow())
        );
    }

    @Test
    void testOptConv() {
        // given
        var src = """
                {
                    "a": [0, 1, 2, {"b": true}]
                }
                """;
        // when
        var parsed = Greyson.read(src).orElseThrow();
        // then
        assertAll(
                () -> assertTrue(parsed.get("a").isPresent()),
                () -> assertTrue(parsed.get("a")
                        .flatMap(a -> a.get(3))
                        .flatMap(a3 -> a3.get("b"))
                        .flatMap(JsonValue::booleanValue)
                        .orElseThrow()),
                () -> assertTrue(
                        parsed.get("x")
                                .flatMap(x -> x.get(5))
                                .flatMap(JsonValue::decimalValue)
                                .map(BigDecimal::doubleValue)
                                .isEmpty()
                ),
                () -> assertTrue(
                        parsed.get("y").map(JsonValue::longValue).isEmpty()
                )
        );
    }
}
