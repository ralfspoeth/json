package io.github.ralfspoeth.json;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.github.ralfspoeth.json.Builder.arrayBuilder;
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
                Builder.objectBuilder()
                        .put("s", Basic.of("hallo"))
                        .put("b", JsonBoolean.TRUE)
                        .put("r", Builder.objectBuilder()
                                .put("x", Basic.of(5))
                                .build()
                        )
                        .put("array", arrayBuilder().add(JsonNull.INSTANCE).build())
                        .build(),
                jo
        );
    }
}
