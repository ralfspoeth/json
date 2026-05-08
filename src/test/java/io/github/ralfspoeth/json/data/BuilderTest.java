package io.github.ralfspoeth.json.data;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;

import static io.github.ralfspoeth.json.data.Builder.*;
import static io.github.ralfspoeth.json.data.JsonBoolean.FALSE;
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
                () -> assertEquals(1, array.elements().getLast().elements().getFirst().decimal().map(BigDecimal::intValue).orElseThrow())
        );
    }

    @Test
    void testToJsonArray() {
        // given
        var l = List.of(Basic.of(5), JsonNull.INSTANCE, TRUE,
                new JsonArray(List.of()), new JsonObject(Map.of()));
        // when
        var r = l.stream().collect(Collector.of(
                ArrayList::new, ArrayList::add,
                (l1, l2) -> {
                    l1.addAll(l2);
                    return l1;
                },
                JsonArray::new)
        );
        // then
        assertAll(
                () -> assertInstanceOf(JsonArray.class, r),
                () -> assertEquals(l, r.elements())
        );
    }

    @Test
    void testOfAny() {
        var job = objectBuilder(new JsonObject(Map.of("a", TRUE)));
        var jab = arrayBuilder(new JsonArray(List.of(TRUE)));
        var vb = basicBuilder(TRUE);
        assertAll(
                () -> assertInstanceOf(Builder.ObjectBuilder.class, job),
                () -> assertInstanceOf(Builder.ArrayBuilder.class, jab),
                () -> assertInstanceOf(Builder.BasicBuilder.class, vb),
                () -> assertEquals(TRUE, job.build().get("a").orElse(JsonNull.INSTANCE)),
                () -> assertEquals(TRUE, jab.build().get(0).orElse(JsonNull.INSTANCE)),
                () -> assertEquals(TRUE, vb.build())
        );
    }

    // --- Builder.of(JsonValue) dispatching ---

    @Test
    void testBuilderOfDispatchesByType() {
        var jo = new JsonObject(Map.of("a", TRUE));
        var ja = new JsonArray(List.of(TRUE, FALSE));
        Basic<?> b = TRUE;
        assertAll(
                () -> assertInstanceOf(ObjectBuilder.class, Builder.of(jo)),
                () -> assertInstanceOf(ArrayBuilder.class, Builder.of(ja)),
                () -> assertInstanceOf(BasicBuilder.class, Builder.of(b)),
                () -> assertEquals(jo, Builder.of(jo).build()),
                () -> assertEquals(ja, Builder.of(ja).build()),
                () -> assertEquals(b, Builder.of(b).build())
        );
    }

    // --- size() / isEmpty() across all three builder kinds ---

    @Test
    void testEmptyBuildersReportZeroSizeAndEmpty() {
        var ob = objectBuilder();
        var ab = arrayBuilder();
        assertAll(
                () -> assertEquals(0, ob.size()),
                () -> assertTrue(ob.isEmpty()),
                () -> assertEquals(0, ab.size()),
                () -> assertTrue(ab.isEmpty())
        );
    }

    @Test
    void testBuilderSizeReflectsContent() {
        var ob = objectBuilder().putBasic("a", 1).putBasic("b", 2).putBasic("c", 3);
        var ab = arrayBuilder().addBasic(1).addBasic(2).addBasic(3).addBasic(4);
        assertAll(
                () -> assertEquals(3, ob.size()),
                () -> assertFalse(ob.isEmpty()),
                () -> assertEquals(4, ab.size()),
                () -> assertFalse(ab.isEmpty())
        );
    }

    @Test
    void testBasicBuilderSizeIsAlwaysOne() {
        var bb = basicBuilder(TRUE);
        assertAll(
                () -> assertEquals(1, bb.size()),
                () -> assertFalse(bb.isEmpty())
        );
    }

    // --- ArrayBuilder details ---

    @Test
    void testArrayBuilderDataExposesUnderlyingList() {
        var ab = arrayBuilder().addBasic(1).addBasic(2);
        var data = ab.data();
        assertAll(
                () -> assertEquals(2, data.size()),
                () -> assertEquals(Basic.of(1), data.getFirst().build()),
                () -> assertEquals(Basic.of(2), data.getLast().build())
        );
    }

    @Test
    void testArrayBuilderRemoveByIndex() {
        var ab = arrayBuilder().addBasic("a").addBasic("b").addBasic("c");
        ab.remove(1);
        var built = ab.build();
        assertAll(
                () -> assertEquals(2, built.elements().size()),
                () -> assertEquals(new JsonString("a"), built.elements().getFirst()),
                () -> assertEquals(new JsonString("c"), built.elements().getLast())
        );
    }

    @Test
    void testArrayBuilderRemoveOutOfBoundsThrows() {
        var ab = arrayBuilder().addBasic(1);
        assertThrows(IndexOutOfBoundsException.class, () -> ab.remove(5));
    }

    @Test
    void testArrayBuilderClear() {
        var ab = arrayBuilder().addBasic(1).addBasic(2).addBasic(3);
        assertEquals(3, ab.size());
        ab.clear();
        assertAll(
                () -> assertEquals(0, ab.size()),
                () -> assertTrue(ab.isEmpty()),
                () -> assertTrue(ab.build().elements().isEmpty())
        );
    }

    @Test
    void testArrayBuilderAddAll() {
        var elements = List.of(TRUE, FALSE, JsonNull.INSTANCE);
        var ab = arrayBuilder().addBasic(0).addAll(elements);
        var built = ab.build();
        assertAll(
                () -> assertEquals(4, built.elements().size()),
                () -> assertEquals(Basic.of(0), built.elements().getFirst()),
                () -> assertEquals(TRUE, built.elements().get(1)),
                () -> assertEquals(FALSE, built.elements().get(2)),
                () -> assertEquals(JsonNull.INSTANCE, built.elements().get(3))
        );
    }

    @Test
    void testArrayBuilderAddAllEmpty() {
        var ab = arrayBuilder().addAll(List.of());
        assertTrue(ab.isEmpty());
    }

    // --- ObjectBuilder details ---

    @Test
    void testObjectBuilderDataExposesUnderlyingMap() {
        var ob = objectBuilder().putBasic("a", 1).putBasic("b", 2);
        var data = ob.data();
        assertAll(
                () -> assertEquals(2, data.size()),
                () -> assertTrue(data.containsKey("a")),
                () -> assertTrue(data.containsKey("b")),
                () -> assertEquals(Basic.of(1), data.get("a").build()),
                () -> assertEquals(Basic.of(2), data.get("b").build())
        );
    }

    @Test
    void testObjectBuilderMergeMap() {
        var ob = objectBuilder().putBasic("a", 1).putBasic("b", 2);
        var update = Map.<String, JsonValue>of("b", Basic.of(20), "c", Basic.of(3));
        var built = ob.merge(update).build();
        assertAll(
                () -> assertEquals(3, built.members().size()),
                () -> assertEquals(Basic.of(1), built.get("a").orElseThrow()),
                () -> assertEquals(Basic.of(20), built.get("b").orElseThrow()),
                () -> assertEquals(Basic.of(3), built.get("c").orElseThrow())
        );
    }

    @Test
    void testObjectBuilderUpdateMapOnlyTouchesExistingKeys() {
        var ob = objectBuilder().putBasic("a", 1).putBasic("b", 2);
        var update = Map.<String, JsonValue>of("a", Basic.of(100), "c", Basic.of(3));
        var built = ob.update(update).build();
        assertAll(
                // only "a" is updated; "c" is ignored because it didn't exist
                () -> assertEquals(2, built.members().size()),
                () -> assertEquals(Basic.of(100), built.get("a").orElseThrow()),
                () -> assertEquals(Basic.of(2), built.get("b").orElseThrow()),
                () -> assertTrue(built.get("c").isEmpty())
        );
    }

    @Test
    void testObjectBuilderInsertMapDoesNotOverwriteExistingKeys() {
        var ob = objectBuilder().putBasic("a", 1);
        var insert = Map.<String, JsonValue>of("a", Basic.of(999), "b", Basic.of(2));
        var built = ob.insert(insert).build();
        assertAll(
                // "a" is preserved, "b" is added
                () -> assertEquals(Basic.of(1), built.get("a").orElseThrow()),
                () -> assertEquals(Basic.of(2), built.get("b").orElseThrow())
        );
    }

    @Test
    void testObjectBuilderPutRejectsNullName() {
        var ob = objectBuilder();
        assertThrows(NullPointerException.class, () -> ob.put(null, TRUE));
    }

    @Test
    void testObjectBuilderPutRejectsNullValue() {
        var ob = objectBuilder();
        assertThrows(NullPointerException.class, () -> ob.put("a", (JsonValue) null));
    }

    @Test
    void testObjectBuilderPutBasicAcceptsNullValueAsJsonNull() {
        var built = objectBuilder().putBasic("a", null).build();
        assertEquals(JsonNull.INSTANCE, built.get("a").orElseThrow());
    }

    // --- BasicBuilder ---

    @Test
    void testBasicBuilderGetReturnsCurrentValue() {
        var bb = basicBuilder(TRUE);
        assertEquals(TRUE, bb.get());
    }

    @Test
    void testBasicBuilderSetReplacesValue() {
        var bb = basicBuilder(TRUE);
        var returned = bb.set(FALSE);
        assertAll(
                () -> assertSame(bb, returned, "set() should return the builder for fluent chaining"),
                () -> assertEquals(FALSE, bb.get()),
                () -> assertEquals(FALSE, bb.build())
        );
    }

    @Test
    void testBasicBuilderRejectsNullOnConstruction() {
        assertThrows(NullPointerException.class, () -> basicBuilder(null));
    }

    @Test
    void testBasicBuilderSetRejectsNull() {
        var bb = basicBuilder(TRUE);
        assertThrows(NullPointerException.class, () -> bb.set(null));
    }

    // --- Fluent API: every mutation returns `this` ---

    @Test
    void testFluentChainingReturnsSameInstance() {
        var ab = arrayBuilder();
        var ob = objectBuilder();
        assertAll(
                () -> assertSame(ab, ab.addBasic(1)),
                () -> assertSame(ab, ab.add(TRUE)),
                () -> assertSame(ab, ab.addAll(List.of())),
                () -> assertSame(ab, ab.remove(0)),
                () -> assertSame(ab, ab.clear()),
                () -> assertSame(ob, ob.putBasic("a", 1)),
                () -> assertSame(ob, ob.put("b", TRUE)),
                () -> assertSame(ob, ob.merge(Map.of())),
                () -> assertSame(ob, ob.update(Map.of())),
                () -> assertSame(ob, ob.insert(Map.of())),
                () -> assertSame(ob, ob.remove("a")),
                () -> assertSame(ob, ob.removeAll(List.of()))
        );
    }

}