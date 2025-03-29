package io.github.ralfspoeth.json;

import org.junit.jupiter.api.Test;

import static io.github.ralfspoeth.json.Aggregate.arrayBuilder;
import static io.github.ralfspoeth.json.Aggregate.objectBuilder;
import static org.junit.jupiter.api.Assertions.*;

class NullsTest {

    @Test
    void basicsNeverNull() {
        assertAll(
                () -> assertNotNull(Basic.of(null)),
                () -> assertNotNull(new JsonString(null).value())
        );

    }

    @Test
    void arraysNeverContainNull() {
        assertAll(
                () -> assertEquals(JsonNull.INSTANCE, arrayBuilder().basic(null).build().elements().getFirst()),
                () -> assertEquals(JsonNull.INSTANCE, arrayBuilder().element(null).build().elements().getFirst())
        );
    }

    @Test
    void objectsNeverContainNulls() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> objectBuilder().named(null, JsonNull.INSTANCE)),
                () -> assertEquals(JsonNull.INSTANCE, objectBuilder().basic("nix", null)
                        .build()
                        .get("nix", Basic.class)
                ),
                () -> {}
        );
    }

}
