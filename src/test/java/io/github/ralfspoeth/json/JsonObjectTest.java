package io.github.ralfspoeth.json;

import org.junit.jupiter.api.Test;

import static io.github.ralfspoeth.json.Aggregate.arrayBuilder;
import static org.junit.jupiter.api.Assertions.*;

class JsonObjectTest {

    @Test
    void testOfRecord() {
        record R(int x) {
        }
        record S(String s, boolean b, R r, Object[] array) {
        }
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
