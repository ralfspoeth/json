package io.github.ralfspoeth.json.suite;

import io.github.ralfspoeth.json.Element;
import io.github.ralfspoeth.json.io.JsonReader;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JSONTestSuiteTest {

    record Result(Path p, Element element, Throwable exception) {
    }

    private final List<Result> acceptedNs = new ArrayList<>();
    private final List<Result> rejectedYs = new ArrayList<>();
    private final List<Result> acceptedIs = new ArrayList<>();
    private final List<Result> rejectedIs = new ArrayList<>();

    // parse a single JSON file
    Result parse(Path p) {
        try (var rdr = new JsonReader(Files.newBufferedReader(p, StandardCharsets.UTF_8))) {
            return new Result(p.getFileName(), rdr.readElement(), null);
        } catch (Throwable t) {
            return new Result(p.getFileName(), null, t);
        }
    }

    Predicate<Path> fileNameFilter(FileSystem fs, String pattern) {
        return p -> fs.getPathMatcher("glob:" + pattern).matches(p.getFileName());
    }

    void collectYs(Path resourceDir) throws IOException {
        try (var files = Files.list(resourceDir)) {
            files.filter(fileNameFilter(resourceDir.getFileSystem(), "y*.json"))
                    .map(this::parse)
                    .filter(r -> r.exception != null)
                    .forEach(rejectedYs::add);
        }
    }

    void collectNs(Path resourceDir) throws IOException {
        try (var files = Files.list(resourceDir)) {
            files.filter(fileNameFilter(resourceDir.getFileSystem(), "n*.json"))
                    .map(this::parse)
                    .filter(r -> r.exception == null && r.element != null)
                    .forEach(acceptedNs::add);
        }
    }

    void collectIs(Path resourceDir) throws IOException {
        try (var files = Files.list(resourceDir)) {
            files.filter(fileNameFilter(resourceDir.getFileSystem(), "i*.json"))
                    .map(this::parse)
                    .forEach(r -> {
                        if (r.exception == null) {
                            acceptedIs.add(r);
                        } else {
                            rejectedIs.add(r);
                        }
                    });
        }
    }

    @Test
    void testAllFiles() throws Exception {
        var resourcesDir = Path.of("src/test/resources");
        collectNs(resourcesDir);
        collectYs(resourcesDir);
        collectIs(resourcesDir);
        try {
            assertAll(
                    () -> assertEquals(0, acceptedNs.size(), "Ns"),
                    () -> assertEquals(0, rejectedYs.size(), "Ys"),
                    () -> assertEquals(0, rejectedIs.size(), "iNs"),
                    () -> assertEquals(0, acceptedIs.size(), "iYs")
            );
        } catch (MultipleFailuresError mfe) {
            for (var f : mfe.getFailures()) {
                System.out.println("Path\tShould accept\tDid accept\tElement\tException");
                switch (f) {
                    case AssertionFailedError afe -> {
                        if (afe.getMessage().startsWith("Ns")) {
                            listResults(acceptedNs, "no", "yes");
                        } else if (afe.getMessage().startsWith("Ys")) {
                            listResults(rejectedYs, "yes", "no");
                        } else if (afe.getMessage().startsWith("iNs")) {
                            mfe.addSuppressed(afe);
                            listResults(rejectedIs, "may", "no");
                        } else if (afe.getMessage().startsWith("iYs")) {
                            mfe.addSuppressed(afe);
                            listResults(acceptedIs, "may", "no");
                        } else {
                            throw new AssertionError();
                        }
                    }
                    case Throwable t -> System.out.println(t.getMessage());
                }
            }
            throw mfe;
        }

    }

    String maxLength(String s, int max) {
        return max > s.length() ? s : s.substring(0, max - 3) + "...";
    }

    String formatJson(String json) {
        return maxLength(json.replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t"), 40);
    }

    void listResults(List<Result> results, String should, String did) {
        results.forEach(r -> System.out.printf("%s\t%s\t%s\t%s\t%s%n",
                r.p,
                should,
                did,
                r.element == null ? "" : formatJson(r.element.json()),
                r.exception == null ? "" : r.exception)
        );
    }
}
