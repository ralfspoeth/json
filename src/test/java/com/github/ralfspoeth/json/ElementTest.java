package com.github.ralfspoeth.json;

import com.github.ralfspoeth.json.io.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ElementTest {

    @Test
    void testElem(){
        Element element = new JsonObject(Map.of(
                "name", new JsonString("Hallo"),
                "value", new JsonArray(List.of(JsonNull.INSTANCE, JsonBoolean.TRUE, JsonBoolean.FALSE, new JsonNumber(5)))
        ));
        System.out.println(JsonWriter.toJson(element));
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
                .filter(e->e instanceof JsonObject)
                .map(JsonObject.class::cast)
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertThrows(UnsupportedOperationException.class, () -> je.elements().add(JsonNull.INSTANCE)),
                () -> assertThrows(UnsupportedOperationException.class, () -> je.elements().remove(0)),
                () -> assertThrows(UnsupportedOperationException.class, () -> obj.members().put("c", JsonBoolean.TRUE))

        );
    }
}