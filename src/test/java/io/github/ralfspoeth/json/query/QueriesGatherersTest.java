package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.Greyson;
import io.github.ralfspoeth.json.data.Basic;
import io.github.ralfspoeth.json.data.JsonValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.data.Builder.objectBuilder;
import static io.github.ralfspoeth.json.query.Queries.*;
import static org.junit.jupiter.api.Assertions.*;

class QueriesGatherersTest {

    @Test
    void testDistinctByPointer() throws IOException {
        // given
        var src = """
                [
                    {"id": 1, "v": "first"},
                    {"id": 2, "v": "second"},
                    {"id": 1, "v": "duplicate"}
                ]""";
        var elements = Greyson.readValue(Reader.of(src)).map(JsonValue::elements).orElseThrow();
        // when
        var unique = elements.stream()
                .gather(distinctBy(Pointer.parse("id")))
                .toList();
        // then: first occurrence per id, in encounter order
        assertEquals(List.of(elements.get(0), elements.get(1)), unique);
    }

    @Test
    void testDistinctByCollapsesMissingKeys() {
        // given: two values without an "id" member
        var first = objectBuilder().putBasic("v", 1).build();
        var second = objectBuilder().putBasic("v", 2).build();
        // when
        var unique = Stream.of(first, second)
                .gather(distinctBy(Pointer.parse("id")))
                .toList();
        // then: both map to Optional.empty(), only the first survives
        assertEquals(List.of(first), unique);
    }

    @Test
    void testMergingHistory() {
        // given
        var p1 = objectBuilder().putBasic("a", 1).build();
        var p2 = objectBuilder().putBasic("b", 2).build();
        var p3 = objectBuilder().putBasic("a", 3).build();
        // when
        var history = Stream.of(p1, p2, p3).gather(merging()).toList();
        // then: one running state per patch
        assertEquals(List.of(
                objectBuilder().putBasic("a", 1).build(),
                objectBuilder().putBasic("a", 1).putBasic("b", 2).build(),
                objectBuilder().putBasic("a", 3).putBasic("b", 2).build()
        ), history);
    }

    @Test
    void testMergingLastState() {
        // given
        var patches = Stream.of(
                objectBuilder().putBasic("a", 1).build(),
                objectBuilder().putBasic("b", 2).build(),
                objectBuilder().putBasic("a", 3).build()
        );
        // when
        var state = patches.gather(merging()).reduce((_, second) -> second);
        // then
        assertEquals(
                objectBuilder().putBasic("a", 3).putBasic("b", 2).build(),
                state.orElseThrow()
        );
    }

    @Test
    void testMergingIsShallow() {
        // given: same top-level member with different nested members
        var p1 = objectBuilder().put("o", objectBuilder().putBasic("x", 1).build()).build();
        var p2 = objectBuilder().put("o", objectBuilder().putBasic("y", 2).build()).build();
        // when
        var state = Stream.of(p1, p2).gather(merging()).reduce((_, second) -> second);
        // then: the later "o" replaces the earlier one entirely
        assertEquals(p2, state.orElseThrow());
    }

    @Test
    void testToJsonObjectFromEntries() {
        // given
        List<Map.Entry<String, JsonValue>> entries = List.of(
                Map.entry("a", Basic.of(1)),
                Map.entry("b", Basic.of(2))
        );
        // when/then
        assertEquals(
                objectBuilder().putBasic("a", 1).putBasic("b", 2).build(),
                entries.stream().collect(toJsonObject())
        );
    }

    @Test
    void testToJsonObjectLastWins() {
        // given
        List<Map.Entry<String, JsonValue>> entries = List.of(
                Map.entry("a", Basic.of(1)),
                Map.entry("a", Basic.of(2))
        );
        // when/then
        assertEquals(
                objectBuilder().putBasic("a", 2).build(),
                entries.stream().collect(toJsonObject())
        );
    }

    @Test
    void testToJsonObjectWithKeyAndValueFunctions() {
        // when
        var result = IntStream.range(0, 3)
                .boxed()
                .collect(toJsonObject(i -> "n" + i, Basic::of));
        // then
        assertEquals(
                objectBuilder().putBasic("n0", 0).putBasic("n1", 1).putBasic("n2", 2).build(),
                result
        );
    }

    @Test
    void testToJsonObjectParallel() {
        // when: parallel collection exercises the combiner
        var result = IntStream.range(0, 1000)
                .parallel()
                .boxed()
                .collect(toJsonObject(i -> "k" + i, Basic::of));
        // then
        assertEquals(1000, result.members().size());
        assertEquals(Basic.of(0), result.members().get("k0"));
        assertEquals(Basic.of(999), result.members().get("k999"));
    }
}
