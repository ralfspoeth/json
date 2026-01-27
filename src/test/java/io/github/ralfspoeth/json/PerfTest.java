package io.github.ralfspoeth.json;

import io.github.ralfspoeth.json.data.JsonArray;
import io.github.ralfspoeth.json.data.JsonObject;
import io.github.ralfspoeth.json.data.JsonValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.zip.GZIPInputStream;

import static java.util.Objects.requireNonNull;

class PerfTest {

    private Reader open() throws IOException {
        return new InputStreamReader(new GZIPInputStream(
                requireNonNull(getClass().getResourceAsStream("/very-big-array.json.gz"))
        ));
    }

    @Test
    void testVeryBigArray() throws IOException {
        try (Reader reader = open()) {
            long start = System.currentTimeMillis();
            var array = Greyson.read(reader).orElseThrow();
            var len = switch(array) {
                case JsonArray(var elems) -> elems.size();
                case JsonObject(var map) -> map.size();
                case JsonValue _ -> 1;
            };
            long end = System.currentTimeMillis();
            System.out.println("Parsing took " + (end - start) + " ms to read " + len + " elements.");
        }
    }
}