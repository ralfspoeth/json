package io.github.ralfspoeth.json;

import io.github.ralfspoeth.json.data.Basic;
import io.github.ralfspoeth.json.data.JsonNull;
import io.github.ralfspoeth.json.data.JsonString;
import org.junit.jupiter.api.Test;

import static io.github.ralfspoeth.json.data.Builder.arrayBuilder;
import static io.github.ralfspoeth.json.data.Builder.objectBuilder;
import static org.junit.jupiter.api.Assertions.*;

class NullsTest {

    @Test
    void basicsNeverNull() {
        assertAll(
                () -> assertNotNull(Basic.of(null)),
                () -> assertThrows(NullPointerException.class, () -> new JsonString(null).value())
        );

    }

    @Test
    void arraysNeverContainNull() {
        var ab = arrayBuilder();
        var bn = Basic.of(null);
        ab.add(bn);
        var ja = ab.build();
        var json = ja.json();
        assertAll(
                () -> assertEquals(JsonNull.INSTANCE, arrayBuilder().addBasic(null).build().elements().getFirst()),
                () -> assertEquals(JsonNull.INSTANCE, arrayBuilder().addBasic(null).build().elements().getFirst())
        );
    }

    @Test
    void objectsNeverContainNulls() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> objectBuilder().put(null, JsonNull.INSTANCE)),
                () -> assertEquals(JsonNull.INSTANCE, objectBuilder().putBasic("nix", null)
                        .build()
                        .get("nix").orElseThrow()
                ),
                () -> {}
        );
    }

}
