package io.github.ralfspoeth.json.samples;

import io.github.ralfspoeth.json.Greyson;
import io.github.ralfspoeth.json.data.JsonNull;
import io.github.ralfspoeth.json.data.JsonValue;
import io.github.ralfspoeth.json.query.Queries;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/**
 * These tests only run on demand
 */
class OpenFigiIT implements AutoCloseable {

    static final URI OPENFIGI_BASE = URI.create("https://api.openfigi.com/v3/");
    static final URI OPENFIGI_VALUES = OPENFIGI_BASE.resolve("mapping/values/");

    final HttpClient client = HttpClient.newHttpClient();

    @Test
    void testGetMappingValues() {
        // given
        Stream.of("exchCode", "idType", "micCode", "currency", "marketSecDes", "securityType", "securityType2")
                .map(this::values)
                .map(v -> v.get("values").orElseThrow())
                .map(Queries::asObject)
                .forEach(o -> assertAll(
                        () -> assertInstanceOf(List.class, o),
                        () -> assertTrue(o instanceof List<?> l && l.stream().allMatch(String.class::isInstance))
                ));

    }

    private JsonValue values(String which) {
        var request = HttpRequest
                .newBuilder(OPENFIGI_VALUES.resolve(which))
                .build();

        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                return JsonNull.INSTANCE;
            } else {
                return Greyson.readValue(new InputStreamReader(response.body(), UTF_8))
                        .orElseThrow();
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void close() {
        client.close();
    }
}
