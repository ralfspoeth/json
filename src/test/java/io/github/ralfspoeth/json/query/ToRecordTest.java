package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.JsonArray;
import io.github.ralfspoeth.json.JsonObject;
import io.github.ralfspoeth.json.io.JsonReader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.query.Queries.*;
import static org.junit.jupiter.api.Assertions.*;

class ToRecordTest {

    @Test
    void toR() {
        // given
        record R(int x) {}
        var src = """
                {
                    "x": 1
                }
                """;

        // when
        var jo = JsonReader.readElement(src);
        var result = new R(
                intValue(members(jo).get("x"), 0)
        );

        // then
        assertAll(
                () -> assertInstanceOf(R.class, result),
                () -> assertInstanceOf(JsonObject.class, jo),
                () -> assertEquals(new R(1), result)
        );
    }

    @Test
    void toRs() {
        // given
        record R(int x) {}
        var src = """
                [{
                    "x": 1
                }, {
                    "x": 2, "y": 3
                }, {}, {
                    "y": 5
                }
                ]
                """;

        // when
        var value = JsonReader.readElement(src);
        var result = elements(value)
                .stream()
                .map(Queries::members)
                .map(jo -> new R(
                        intValue(jo.get("x"), 0)
                ))
                .toList();

        // then
        assertAll(
                () -> assertInstanceOf(JsonArray.class, value),
                () -> assertAll(result.stream().map(r -> () -> assertInstanceOf(R.class, r))),
                () -> assertEquals(List.of(new R(1), new R(2), new R(0), new R(0)), result)
        );
    }

    @Test
    void toLargeRec() {
        // nested data structure
        record Nested(int x, int y) {}
        record NestedList(List<Nested> l) {}
        record Large(int x, double y, String z, boolean b, Nested n, NestedList nl) {}
        // source text
        var src = """
                {
                    "x": 1,
                    "y": 2.0,
                    "z": "hello",
                    "b": true,
                    "n": {
                        "x": -8,
                        "y": -4.5
                    },
                    "nl": [
                        {"x": 1, "y": 2},
                        {"x": 3, "y": 4}
                    ]
                }
                """;
        // JSON object representation
        var jo = JsonReader.readElement(src);
        // convert to singleton list of Large instances
        var result = Stream.of(jo).map(Queries::members).map(om -> new Large(
                intValue(om.get("x"), 0),
                doubleValue(om.get("y"), 0),
                stringValue(om.get("z"), null),
                booleanValue(om.get("b"), false),
                new Nested(
                        intValue(members(om.get("n")).get("x"), 0),
                        intValue(members(om.get("n")).get("y"), 0)
                ),
                new NestedList(
                        elements(om.get("nl")).stream()
                                .map(Queries::members)
                                .map(nlm -> new Nested(
                                        intValue(nlm.get("x"), 0),
                                        intValue(nlm.get("y"), 0))
                                )
                                .toList()
                )
        )).toList();
        // assert that result is a singleton list of Large instances and its only instance matches what
        // we expect
        assertAll(
                () -> assertEquals(1, result.size()),
                () -> assertEquals(new Large(1, 2.0, "hello", true,
                                new Nested(-8, -4),
                                new NestedList(List.of(new Nested(1, 2), new Nested(3, 4)))),
                        result.getFirst()
                )
        );
    }

}
