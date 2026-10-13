package io.github.ralfspoeth.json.samples;

import io.github.ralfspoeth.json.Greyson;
import io.github.ralfspoeth.json.data.JsonValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.function.Function;

import static io.github.ralfspoeth.json.query.Pointer.self;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * cf. Viktor Klang's JEP 540 introduction.
 */
class ConfigTest {

    record Config(Charset charset, int port) {
        Config {
            requireNonNull(charset);
        }

        static Config DEFAULT = new Config(
                UTF_8,
                8080
        );
    }

    // config.json
    static final String CONFIG = """
            {
                "port": 8080,
                "charset": "UTF-8"
            }
            """;

    static final String NO_PORT = """
            {
                "charset": "UTF-8"
            }
            """;

    static final String NO_CHARWSET = """
            {
                "port": 8080
            }
            """;

    @Test
    void testConfigAllPresent() throws IOException {
        var cfg = Greyson.readValue(Reader.of(CONFIG)).orElseThrow();
        var charset = cfg.get("charset")
                .flatMap(JsonValue::string)
                .map(Charset::forName)
                .orElseThrow();
        var port = cfg.get("port").flatMap(JsonValue::decimal)
                .map(BigDecimal::intValue)
                .orElseThrow();
        assertEquals(Config.DEFAULT, new Config(charset, port));

    }

    @Test
    void testConfigAllAbsent() throws IOException {
        var cfg = Greyson.readValue(Reader.of("{}"))
                .map(jsonToConfig())
                .orElseThrow();
        assertEquals(Config.DEFAULT, cfg);
    }

    @Test
    void testNoPort() throws IOException {
        var cfg = Greyson.readValue(Reader.of(NO_PORT)).map(jsonToConfig()).orElseThrow();
        assertEquals(Config.DEFAULT, cfg);
    }

    @Test
    void testNoCharset() throws IOException {
        var cfg = Greyson.readValue(Reader.of(NO_CHARWSET)).map(jsonToConfig()).orElseThrow();
        assertEquals(Config.DEFAULT, cfg);
    }

    static Function<JsonValue, Config> jsonToConfig() {
        var ptrCharset = self().member("charset");
        var ptrPort = self().member("port");

        return v -> new Config(
                ptrCharset.stringValue(v)
                        .map(Charset::forName)
                        .orElse(UTF_8),
                ptrPort.intValue(v)
                        .orElse(8080)
        );
    }

}
