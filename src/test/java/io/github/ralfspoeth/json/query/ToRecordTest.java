package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.Greyson;
import io.github.ralfspoeth.json.data.JsonArray;
import io.github.ralfspoeth.json.data.JsonObject;
import io.github.ralfspoeth.json.data.JsonValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

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
        var jo = Greyson.read(src).orElseThrow();
        var result = new R(jo.get("x").flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow());

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
        var value = Greyson.read(src).orElseThrow();
        var result = value.elements()
                .stream()
                .map(jo -> new R(
                        jo.get("x").flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElse(0)
                ))
                .toList();

        // then
        assertAll(
                () -> assertInstanceOf(JsonArray.class, value),
                () -> assertAll(result.stream().map(r -> () -> assertInstanceOf(R.class, r))),
                () -> assertEquals(List.of(new R(1), new R(2), new R(0), new R(0)), result)
        );
    }
}
