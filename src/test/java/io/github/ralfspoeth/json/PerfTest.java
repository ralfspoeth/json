package io.github.ralfspoeth.json;

import io.github.ralfspoeth.json.data.JsonArray;
import io.github.ralfspoeth.json.data.JsonObject;
import io.github.ralfspoeth.json.data.JsonValue;
import io.github.ralfspoeth.utf8.Utf8Reader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

import static java.util.Objects.requireNonNull;

class PerfTest {

    @Test
    void testUtf8Stream() throws IOException {
        try (var is = getClass().getResourceAsStream("/very-big-array.json.gz");
             var gis = new GZIPInputStream(requireNonNull(is))) {
            long start = System.currentTimeMillis();
            var array = Greyson.read(new Utf8Reader(gis)).orElseThrow();
            long end = System.currentTimeMillis();
            var len = switch (array) {
                case JsonArray(var elems) -> elems.size();
                case JsonObject(var map) -> map.size();
                case JsonValue _ -> 1;
            };
            var dep = array.depth();
            System.out.println("InputStreamReader took " + (end - start) + " ms to read " + len + " elements with depths " + dep);
        }
    }

    @Test
    void testFastUtf8Stream() throws IOException {
        try (var is = getClass().getResourceAsStream("/very-big-array.json.gz");
             var gis = new GZIPInputStream(requireNonNull(is))) {
            long start = System.currentTimeMillis();
            var array = Greyson.readBuilder(new Utf8Reader(gis)).orElseThrow();
            var len = array.size();
            long end = System.currentTimeMillis();
            System.out.println("FastReader into Builder took " + (end - start) + " ms to read " + len + " elements");
        }
    }


    @Test
    void testVeryBigArray() throws IOException {
        try (var gis = new GZIPInputStream(requireNonNull(getClass().getResourceAsStream("/very-big-array.json.gz")));
             var reader = new InputStreamReader(gis)) {
            long start = System.currentTimeMillis();
            var array = Greyson.read(reader).orElseThrow();
            long end = System.currentTimeMillis();
            var len = switch (array) {
                case JsonArray(var elems) -> elems.size();
                case JsonObject(var map) -> map.size();
                case JsonValue _ -> 1;
            };
            var dep = array.depth();
            System.out.println("FastUtf8Reader took " + (end - start) + " ms to read " + len + " elements with depths " + dep);
        }
    }
}