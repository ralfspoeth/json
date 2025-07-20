package io.github.ralfspoeth.json;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.github.ralfspoeth.json.Aggregate.arrayBuilder;
import static org.junit.jupiter.api.Assertions.*;

class JsonObjectTest {

    @Test
    void testImmutable() {
        var jo = new JsonObject(new HashMap<>());
        assertAll(
                () -> assertThrows(UnsupportedOperationException.class, () -> jo.members().put("x", JsonNull.INSTANCE))
        );
    }

    @Test
    void testNoNulls() {
        Map<String, JsonValue> map = new HashMap<>();
        map.put("x", null);
        assertThrows(NullPointerException.class, () -> new JsonObject(map));
    }


    @Test
    void testOfRecord() {
        record R(int x) {}
        record S(String s, boolean b, R r, Object[] array) {}
        var r = new R(5);
        var s = new S("hallo", true, r, new Object[]{null});
        var jo = JsonObject.ofRecord(s);
        assertEquals(
                Aggregate.objectBuilder()
                        .named("s", Basic.of("hallo"))
                        .named("b", JsonBoolean.TRUE)
                        .named("r", Aggregate.objectBuilder()
                                .named("x", Basic.of(5))
                                .build()
                        )
                        .named("array", arrayBuilder().item(JsonNull.INSTANCE).build())
                        .build(),
                jo
        );
    }
}
