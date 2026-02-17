package io.github.ralfspoeth.json.data;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static io.github.ralfspoeth.json.data.Builder.arrayBuilder;
import static io.github.ralfspoeth.json.data.Builder.objectBuilder;
import static org.junit.jupiter.api.Assertions.*;

class NaviTest {

    @Test
    void testSimpleNavigations() {
        // given
        var jo = objectBuilder()
                .put("a", objectBuilder()
                        .put("b", arrayBuilder()
                                .add(JsonBoolean.TRUE)
                                .add(Basic.of(1))))
                .build();
        // when
        var a = jo.get("a");
        var ab = jo.get("a").flatMap(o -> o.get("b"));
        var abtrue = jo.get("a").flatMap(o -> o.get("b")).flatMap(arr -> arr.get(0));
        var ab1 = jo.get("a").flatMap(o -> o.get("b")).flatMap(arr -> arr.get(1));
        // then
        assertAll(
                () -> assertTrue(a.isPresent()),
                () -> assertTrue(ab.isPresent()),
                () -> assertTrue(abtrue.isPresent()),
                () -> assertEquals(true, abtrue.flatMap(JsonValue::bool).orElseThrow()),
                () -> assertEquals(1, ab1.flatMap(JsonValue::decimal).map(BigDecimal::intValue).orElseThrow())
        );
    }

}
