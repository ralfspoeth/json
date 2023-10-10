package com.github.ralfspoeth.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ElementTest {

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
                () -> assertThrows(UnsupportedOperationException.class, () -> je.elements().remove(0)),
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
                Element.arrayBuilder()
                        .item(JsonNumber.ZERO)
                        .build(),
                Element.objectBuilder()
                        .named("a", JsonBoolean.TRUE)
                        .named("b", Element.arrayBuilder())
                        .build()
        );
        var l = str.map(e -> switch (e) {
            case JsonBoolean.TRUE -> "true";
            case JsonBoolean.FALSE -> "false";
            case JsonNull _ -> "null";
            case Basic b -> b.json();
            case Aggregate a -> a.toString();
        }).toList();
        System.out.println(l);
    }
}
