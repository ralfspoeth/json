package io.github.ralfspoeth.json;

import org.junit.jupiter.api.Test;

import static io.github.ralfspoeth.json.Aggregate.objectBuilder;
import static org.junit.jupiter.api.Assertions.*;

class BuilderTest {

    @Test
    void testObjectBuilderMergeUpdateRemove() {
        var one = objectBuilder().named("a", 1).named("b", 2).build();
        var two = objectBuilder().named("b", 3).named("c", 4).build();
        var merged = Aggregate.builder(one).merge(two).build();
        var expected = objectBuilder().named("a", 1).named("b", 3).named("c", 4).build();
        assertAll(
                () -> assertEquals(expected, merged),
                () -> assertEquals(objectBuilder().named("a", 1).build(), Aggregate.builder(one).remove("b").build()),
                () -> assertEquals(objectBuilder().named("a", 1).build(), Aggregate.builder(one).removeAll(two).build()),
                () -> assertEquals(objectBuilder().named("a", 1).named("b", 3).build(), Aggregate.builder(one).update(two).build())
        );

    }

    @Test
    void testObjectBuilder() {
        var obj = objectBuilder()
                .named("name", "Ralf")
                .named("income", 5)
                .named("sex", true)
                .named("seven", new JsonString("murks"))
                .namedNull("nix")
                .named("adr", Aggregate.arrayBuilder()
                        .item(5)
                        .item(objectBuilder().named("sowat", "nix").build())
                        .item(true)
                        .item(false)
                        .nullItem()
                        .build()
                )
                .build();
        assertAll(
                () -> assertEquals(6, obj.members().size()),
                () -> assertEquals(5, obj.get("adr", JsonArray.class).elements().size())
        );
    }

    @Test
    void testDuplicateName() {
        var aIsFalse = objectBuilder()
                .named("a", Basic.of(true))
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