package io.github.ralfspoeth.json.data;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

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
}
