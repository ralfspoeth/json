package io.github.ralfspoeth.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuilderTest {

    @Test
    void testObjectBuilder() {
        var obj = Aggregate.objectBuilder()
                .named("name", "Ralf")
                .named("income", 5)
                .named("sex", true)
                .named("seven", new JsonString("murks"))
                .namedNull("nix")
                .named("adr", Aggregate.arrayBuilder()
                        .item(5)
                        .item(Aggregate.objectBuilder().named("sowat", "nix"))
                        .item(true)
                        .item(false)
                        .nullItem()
                )
                .build();
        assertAll(
                () -> assertEquals(6, obj.members().size()),
                () -> assertEquals(5, obj.get("adr", JsonArray.class).elements().size())
        );
    }

    @Test
    void testDuplicateName() {
        var aIsFalse = Aggregate.objectBuilder().named("a", Basic.of(true))
                .named("a", Basic.of(false))
                .build();
        assertEquals(JsonBoolean.FALSE, aIsFalse.get("a", JsonBoolean.class));
    }

    @Test
    void testArrayBuilder() {
        var array = Aggregate.arrayBuilder()
                .item(JsonBoolean.TRUE)
                .item(JsonNull.INSTANCE)
                .item(JsonBoolean.FALSE)
                .item(Basic.of("hallo"))
                .item(Basic.of(1d))
                .build();
        assertAll(
                () -> assertEquals(5, array.elements().size()),
                () -> assertEquals(2, array
                        .elements()
                        .stream()
                        .filter(i -> i instanceof JsonBoolean)
                        .count()
                ),
                () -> assertTrue(array.elements().contains(JsonNull.INSTANCE)),
                () -> assertTrue(array.elements().contains(new JsonNumber(1))),
                () -> assertTrue(array.elements().contains(new JsonString("hallo")))
        );
    }

}