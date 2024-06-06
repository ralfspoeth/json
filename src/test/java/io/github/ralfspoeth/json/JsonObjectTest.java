package io.github.ralfspoeth.json;

import org.junit.jupiter.api.Test;

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
        System.out.println(jo);
    }
}
