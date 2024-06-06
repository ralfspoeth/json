package io.github.ralfspoeth.json;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.github.ralfspoeth.json.Aggregate.objectBuilder;
import static org.junit.jupiter.api.Assertions.*;

class BuilderTest {

    @Test
    void testObjectBuilderMergeUpdateRemove() {
        var one = objectBuilder().basic("a", 1).basic("b", 2).build();
        var two = objectBuilder().basic("b", 3).basic("c", 4).build();
        var merged = Aggregate.objectBuilder(one).merge(two).build();
        var expected = objectBuilder().basic("a", 1).basic("b", 3).basic("c", 4).build();
        assertAll(
                () -> assertEquals(expected, merged),
                () -> assertEquals(objectBuilder().basic("a", 1).build(), Aggregate.objectBuilder(one).remove("b").build()),
                () -> assertEquals(objectBuilder().basic("a", 1).build(), Aggregate.objectBuilder(one).removeAll(two).build()),
                () -> assertEquals(objectBuilder().basic("a", 1).basic("b", 3).build(), Aggregate.objectBuilder(one).update(two).build())
        );

    }

    @Test
    void testObjectBuilder() {
        var obj = objectBuilder()
                .basic("name", "Ralf")
                .basic("income", 5)
                .basic("sex", true)
                .named("seven", new JsonString("murks"))
                .basic("nix")
                .named("adr", Aggregate.arrayBuilder()
                        .element(5)
                        .item(objectBuilder().basic("sowat", "nix").build())
                        .element(true)
                        .element(false)
                        .nullItem()
                        .build()
                )
                .build();
        assertAll(
                () -> assertEquals(6, obj.members().size()),
                () -> Assertions.assertEquals(5, obj.get("adr", JsonArray.class).elements().size())
        );
    }

    @Test
    void testDuplicateName() {
        var aIsFalse = objectBuilder()
                .named("a", Basic.of(true))
                .named("a", Basic.of(false))
                .build();
        Assertions.assertEquals(JsonBoolean.FALSE, aIsFalse.get("a", JsonBoolean.class));
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