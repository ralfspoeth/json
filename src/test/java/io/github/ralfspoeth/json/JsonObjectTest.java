package io.github.ralfspoeth.json;

import org.junit.jupiter.api.Test;

import static io.github.ralfspoeth.json.Aggregate.objectBuilder;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonObjectTest {

    @Test
    void test() {
        record A(double x) {
        }

        var json = objectBuilder().named("x", 5).build();

        assertAll(
                () -> assertEquals(new A(5), json.toRecord(A.class))
        );
    }
}
