package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.Greyson;
import io.github.ralfspoeth.json.JsonArray;
import io.github.ralfspoeth.json.JsonObject;
import io.github.ralfspoeth.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        var jo = Greyson.readValue(src);
        var result = new R(jo.get("x").map(x -> intValue(x, 0)).orElse(0));

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
        var value = Greyson.readValue(src);
        var result = value.elements()
                .stream()
                .map(JsonValue::members)
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
        record Nested(int x, long y) {}
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
        var jo = Greyson.readValue(src);
        // convert to singleton list of Large instances
        var result = new Large(
                jo.get("x").map(x -> intValue(x, 0)).orElse(0),
                jo.get("y").map(y -> doubleValue(y, 0d)).orElse(0d),
                jo.get("z").flatMap(JsonValue::stringValue).orElse(null),
                jo.get("b").flatMap(JsonValue::booleanValue).orElse(false),
                new Nested(
                        intValue(jo.get("n").flatMap(n -> n.get("x")).orElseThrow(), 0),
                        longValue(jo.get("n").flatMap(n -> n.get("y")).orElseThrow(), 0)
                ),
                new NestedList(
                        jo.get("nl").stream()
                                .flatMap(nl -> nl.elements().stream())
                                .map(JsonValue::members)
                                .map(v -> new Nested(
                                        intValue(v.get("x"), 0),
                                        longValue(v.get("y"), 0)
                                )).toList()
                )
        );

        // assert that result is a singleton list of Large instances and its only instance matches what
        // we expect
        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(new Large(1, 2.0, "hello", true,
                                new Nested(-8, -4),
                                new NestedList(List.of(new Nested(1, 2), new Nested(3, 4)))),
                        result
                )
        );
    }

}
