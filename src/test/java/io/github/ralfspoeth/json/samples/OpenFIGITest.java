package io.github.ralfspoeth.json.samples;

import io.github.ralfspoeth.json.Greyson;
import io.github.ralfspoeth.json.query.Queries;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class OpenFIGITest implements AutoCloseable {

    static URI OPENFIGI_URI = URI.create("https://api.openfigi.com/v3/");

    final HttpClient client = HttpClient
            .newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

    @Test
    void testGetExchCodeMappings() throws IOException, InterruptedException {
        // given
        var request = HttpRequest
                .newBuilder(OPENFIGI_URI.resolve("mapping/values/exchCode"))
                .GET()
                .build();
        // when
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        var body = Greyson.readValue(new InputStreamReader(response.body(), UTF_8)).orElseThrow();
        var values = body.get("values")
                .map(Queries::asObject)
                .orElseThrow();
        // then
        assertAll(
                () -> assertEquals(200, response.statusCode()),
                () -> assertInstanceOf(List.class, values),
                () -> assertTrue(values instanceof List<?> l && l.stream()
                        .allMatch(String.class::isInstance))
        );
    }


    @Override
    public void close() {
        client.close();
    }
}
