package io.github.ralfspoeth.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.Aggregate.arrayBuilder;
import static io.github.ralfspoeth.json.Aggregate.objectBuilder;
import static org.junit.jupiter.api.Assertions.*;

class JsonValueTest {

    @Test
    void testDepth() {
        assertAll(
                () -> assertEquals(1, objectBuilder().build().depth()),
                () -> assertEquals(2, objectBuilder().named("x", JsonNull.INSTANCE).build().depth()),
                () -> assertEquals(3, objectBuilder().named("x", objectBuilder().named("y", JsonBoolean.TRUE).build()).build().depth())
        );
    }

    @Test
    void testMutability() {
        var je = new JsonArray(
                List.of(
                        JsonNull.INSTANCE, JsonBoolean.FALSE, JsonBoolean.TRUE,
                        new JsonNumber(5), new JsonString("five"),
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
                        .item(JsonNumber.ZERO)
                        .build(),
                objectBuilder()
                        .named("a", JsonBoolean.TRUE)
                        .basic("b", arrayBuilder())
                        .build()
        );
        var l = str.map(e -> switch (e) {
            case JsonBoolean.TRUE -> "true";
            case JsonBoolean.FALSE -> "false";
            case JsonNull ignored -> "null";
            case Basic<?> b -> b.json();
            case Aggregate a -> a.toString();
        }).toList();
        assertNotNull(l);
    }

    @Test
    void testOf() {
        record R(Object x) {}
        assertAll(
                () -> assertEquals(JsonNull.INSTANCE, JsonValue.of(null)),
                () -> assertEquals(new JsonNumber(5), JsonValue.of(5)),
                () -> assertEquals(new JsonObject(Map.of("x", JsonNull.INSTANCE)), JsonValue.of(new R(null))),
                () -> assertEquals(new JsonArray(List.of(JsonBoolean.FALSE)), JsonValue.of(new Object[]{false}))
        );
    }

    @Test
    void testJsonOfRecords() {
        record A(int x) {}
        record B(A a, boolean b) {}
        record C(B b, String s) {}

        // objects to convert
        var a = new A(5);
        var b = new B(a, true);
        var c = new C(b, "Bb");
        // json pendants
        var jsonA = objectBuilder().basic("x", 5).build();
        var jsonB = objectBuilder().named("a", jsonA).basic("b", true).build();
        var jsonC = objectBuilder().named("b", jsonB).basic("s", "Bb").build();

        assertAll(
                () -> assertEquals(jsonA, JsonValue.of(a)),
                () -> assertEquals(jsonB, JsonValue.of(b)),
                () -> assertEquals(jsonC, JsonValue.of(c))
        );
    }

    @Test
    void testDeepStructure() {
        record A(int x) {}
        record B(A a, A b) {}
        Object o = new Object[]{new A(0), new B(new A(1), new A(2)), new Object[]{new int[]{5, 6, 7}}};
        assertEquals(arrayBuilder()
                .item(new JsonObject(Map.of("x", Basic.of(0))))
                .item(new JsonObject(Map.of(
                                "a", new JsonObject(Map.of("x", Basic.of(1))),
                                "b", new JsonObject(Map.of("x", Basic.of(2)))
                        ))
                ).item(arrayBuilder()
                        .item(arrayBuilder()
                                .item(Basic.of(5))
                                .item(Basic.of(6))
                                .item(Basic.of(7))
                                .build()
                        ).build()
                ).build(), JsonValue.of(o));
    }
}
