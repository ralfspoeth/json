package io.github.ralfspoeth.json.suite;


import io.github.ralfspoeth.json.Greyson;
import io.github.ralfspoeth.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.opentest4j.MultipleFailuresError;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JSONTestSuiteTransformTest extends JSONTestSuiteBase {

    private JsonValue serialAndParse(JsonValue element) {
        var str = Greyson.write(new StringBuilder(), element).toString();
        return Greyson.read(str).orElseThrow();
    }

    private Executable testTransform(Path path) {
        return () -> {
            var r = parse(path);
            assertAll(
                    () -> assertNotNull(r.element(), "Null Element " + path),
                    () -> assertNull(r.exception(), "Not null exception " + path),
                    () -> assertEquals(r.element(), serialAndParse(r.element()), "Reverse parse error in " + path)
            );
        };
    }

    @Test
    void transformYs() throws IOException {
        try (var files = Files.list(RESOURCES)) {
            var execs = files.filter(fileNameFilter(RESOURCES.getFileSystem(), "y*.json"))
                    .map(this::testTransform)
                    .toList()
                    .toArray(new Executable[0]);
            assertAll(execs);
        } catch (MultipleFailuresError e) {
            e.getFailures().forEach(Throwable::printStackTrace);
        }
    }
}
