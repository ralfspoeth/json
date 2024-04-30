package io.github.ralfspoeth.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.Aggregate.objectBuilder;
import static org.junit.jupiter.api.Assertions.*;

class ElementTest {

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
                Aggregate.arrayBuilder()
                        .item(JsonNumber.ZERO)
                        .build(),
                objectBuilder()
                        .named("a", JsonBoolean.TRUE)
                        .basic("b", Aggregate.arrayBuilder())
                        .build()
        );
        var l = str.map(e -> switch (e) {
            case JsonBoolean.TRUE -> "true";
            case JsonBoolean.FALSE -> "false";
            case JsonNull ignored -> "null";
            case Basic<?> b -> b.json();
            case Aggregate a -> a.toString();
        }).toList();
        System.out.println(l);
    }

    @Test
    void testOf() {
        record R(Object x){}
        assertAll(
                () -> assertEquals(JsonNull.INSTANCE, Element.of(null)),
                () -> assertEquals(new JsonNumber(5), Element.of(5)),
                () -> assertEquals(new JsonObject(Map.of("x", JsonNull.INSTANCE)), Element.of(new R(null))),
                () -> assertEquals(new JsonArray(List.of(JsonBoolean.FALSE)), Element.of(new Object[]{false}))
        );
    }

    @Test
    void testOfRecords() {
        record A(int x){}
        record B(A a, boolean b){}
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
                () -> assertEquals(jsonA, Element.of(a)),
                () -> assertEquals(jsonB, Element.of(b)),
                () -> assertEquals(jsonC, Element.of(c))
        );
    }
}
