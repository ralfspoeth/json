package io.github.ralfspoeth.json.data;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.data.Builder.*;
import static io.github.ralfspoeth.json.data.JsonBoolean.TRUE;

import static org.junit.jupiter.api.Assertions.*;

class BuilderTest {

    @Test
    void testObjectBuilderMergeUpdateRemove() {
        var one = objectBuilder().putBasic("a", 1).putBasic("b", 2).build();
        var two = objectBuilder().putBasic("b", 3).putBasic("c", 4).build();
        var merged = objectBuilder(one).merge(two).build();
        var expectedMerged = objectBuilder().putBasic("a", 1).putBasic("b", 3).putBasic("c", 4).build();
        var expectedA = objectBuilder().putBasic("a", 1).build();
        assertAll(
                () -> assertEquals(expectedMerged, merged),
                () -> assertEquals(expectedA, objectBuilder(one).remove("b").build()),
                () -> assertEquals(expectedA, objectBuilder(one).removeAll(two.members().keySet()).build()),
                () -> assertEquals(objectBuilder().putBasic("a", 1).putBasic("b", 3).build(), objectBuilder(one).update(two).build())
        );

    }

    @Test
    void testObjectBuilder() {
        var obj = objectBuilder()
                .putBasic("name", "Ralf")
                .putBasic("income", 5)
                .putBasic("sex", true)
                .put("seven", new JsonString("murks"))
                .putBasic("nix", null)
                .put("adr", arrayBuilder()
                        .addBasic(5)
                        .add(objectBuilder().putBasic("sowat", "nix"))
                        .addBasic(true)
                        .addBasic(false)
                        .add(JsonNull.INSTANCE)
                )
                .build();
        assertAll(
                () -> assertEquals(6, obj.members().size()),
                () -> assertEquals(5, obj.get("adr").map(v -> v.elements().size()).orElse(-1))
        );
    }

    @Test
    void testObjectBuilderFromJsonObject() {
        // given
        var jo = new JsonObject(Map.of("a", TRUE, "b", JsonBoolean.FALSE));
        // when
        var ob = objectBuilder(jo);
        // then
        assertAll(
                () -> assertEquals(jo, ob.build())
        );
    }

    @Test
    void testDuplicateName() {
        var aIsFalse = objectBuilder()
                .put("a", Basic.of(true))
                .put("a", Basic.of(false))
                .build();
        Assertions.assertEquals(JsonBoolean.FALSE, aIsFalse.get("a").orElse(JsonNull.INSTANCE));
    }

    @Test
    void testInsertIntoEmpty() {
        // given
        JsonObject jo = new JsonObject(Map.of("a", TRUE, "b", JsonBoolean.FALSE));
        // then
        assertAll(
                () -> assertEquals(jo, objectBuilder().insert(jo).build()),
                () -> assertEquals(jo, objectBuilder().insert(jo).insert(jo).build()),
                () -> assertEquals(jo, objectBuilder().insert(jo.members()).build()),
                () -> assertEquals(jo, objectBuilder().insert(jo.members()).insert(jo.members()).build())
        );
    }

    @Test
    void testInsertInto() {
        // given
        JsonObject jo = new JsonObject(Map.of("a", TRUE, "b", JsonBoolean.FALSE));
        JsonObject simple = new JsonObject(Map.of("x", JsonNull.INSTANCE));
        JsonObject built = new JsonObject(Map.of("a", TRUE, "b", JsonBoolean.FALSE, "x", JsonNull.INSTANCE));
        // then
        assertAll(
                () -> assertEquals(built, objectBuilder(simple).insert(jo).build()),
                () -> assertEquals(built, objectBuilder(jo).insert(simple).build()),
                () -> assertEquals(built, objectBuilder(built).insert(simple).build()),
                () -> assertEquals(built, objectBuilder(built).insert(jo).build()),
                () -> assertEquals(built, objectBuilder(built).insert(simple).insert(jo).build()),
                () -> assertEquals(built, objectBuilder(built).insert(jo).insert(simple).build())
        );
    }

    @Test
    void testArrayBuilder() {
        var array = arrayBuilder()
                .add(TRUE)
                .add(JsonNull.INSTANCE)
                .add(JsonBoolean.FALSE)
                .add(Basic.of("hallo"))
                .add(Basic.of(1d))
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
                () -> assertTrue(array.elements().contains(Basic.of(1))),
                () -> assertTrue(array.elements().contains(new JsonString("hallo")))
        );
    }

    @Test
    void testArrayBuilderItem() {
        // given
        // [{"a": null}, [[1]]
        var array = arrayBuilder()
                .add(objectBuilder().put("a", JsonNull.INSTANCE).build())
                .add(arrayBuilder().addBasic(1).build())
                .build();
        assertAll(
                () -> assertEquals(2, array.elements().size()),
                () -> assertEquals(JsonNull.INSTANCE, array.elements().getFirst().get("a").orElseThrow()),
                () -> assertEquals(1, array.elements().getLast().elements().getFirst().intValue(0))
        );
    }

    @Test
    void testToJsonArray() {
        // given
        var l = List.of(Basic.of(5), JsonNull.INSTANCE, TRUE,
                new JsonArray(List.of()), new JsonObject(Map.of()));
        var s = l.stream();
        // when
        var r = s.collect(toJsonArray());
        // then
        assertAll(
                () -> assertInstanceOf(JsonArray.class, r),
                () -> assertEquals(l, r.elements())
        );
    }

    @Test
    void testBuildersToJsonArray() {
        // given
        var point = new JsonObject(Map.of("x", Basic.of(1), "y", Basic.of(2)));
        var array = new JsonArray(List.of(Basic.of(7), Basic.of(11)));
        // when
        var result = arrayBuilder().add(objectBuilder(point)).add(arrayBuilder(array)).build();
        // then
        assertEquals(result, Stream.of(objectBuilder(point), arrayBuilder(array)).collect(buildersToJsonArray()));
    }

    @Test
    void testOfAny() {
        var job = objectBuilder(new JsonObject(Map.of("a", TRUE)));
        var jab = arrayBuilder(new JsonArray(List.of(TRUE)));
        var vb = basicBuilder(TRUE);
        assertAll(
                () -> assertInstanceOf(Builder.class, job),
                () -> assertInstanceOf(Builder.class, jab),
                () -> assertInstanceOf(Builder.class, vb),
                () -> assertEquals(TRUE, job.build().get("a").orElse(JsonNull.INSTANCE)),
                () -> assertEquals(TRUE, jab.build().get(0).orElse(JsonNull.INSTANCE)),
                () -> assertEquals(TRUE, vb.build())
        );
    }

}