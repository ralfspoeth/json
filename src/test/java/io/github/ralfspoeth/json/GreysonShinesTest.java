package io.github.ralfspoeth.json;

import io.github.ralfspoeth.json.data.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static io.github.ralfspoeth.json.data.Builder.arrayBuilder;
import static io.github.ralfspoeth.json.data.Builder.objectBuilder;
import static io.github.ralfspoeth.json.query.Pointer.parse;
import static io.github.ralfspoeth.json.query.Selector.all;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A worked example of the kind of task Greyson handles more cleanly than a
 * mutable tree model such as Jackson's {@code JsonNode}: a schema-less document
 * that must be walked <em>exhaustively</em>, transformed <em>immutably</em>, and
 * queried across irregular nesting — without binding to any POJO.
 *
 * <p>The contrast in a sentence: the equivalent Jackson code reaches for
 * {@code instanceof ObjectNode/ArrayNode/ValueNode} ladders (no compiler-checked
 * exhaustiveness), mutates nodes in place or {@code deepCopy()}s them (no
 * immutability guarantee), and navigates through nullable {@code JsonNode}/
 * {@code MissingNode} rather than {@code Optional}.</p>
 */
class GreysonShinesTest {

    // A messy, heterogeneous account export. Sensitive fields ("password",
    // "token", "secret", "cardNumber") sit at four different depths.
    private static final String EXPORT = """
            {
              "id": 42,
              "profile": {
                "name": "Ada Lovelace",
                "email": "ada@example.com",
                "password": "hunter2",
                "addresses": [
                  {"kind": "home", "city": "London", "zip": "E1 6AN"},
                  {"kind": "work", "city": "Cambridge", "zip": "CB2 1TN"}
                ]
              },
              "payment": {
                "methods": [
                  {"type": "card", "cardNumber": "4111111111111111", "exp": "12/29"},
                  {"type": "card", "cardNumber": "5500005555555559", "exp": "01/30"}
                ]
              },
              "sessions": [
                {"id": "s-1", "token": "abc.def.ghi", "ip": "10.0.0.1"},
                {"id": "s-2", "token": "uvw.xyz.123", "ip": "10.0.0.2"}
              ],
              "metadata": {
                "version": 3,
                "tags": ["beta", "internal"],
                "audit": {"secret": "do-not-log", "createdBy": "system"}
              }
            }
            """;

    /**
     * Recursively mask the string value of any member whose key is sensitive,
     * at any depth, returning a fresh immutable tree. Because {@link JsonValue}
     * is a sealed hierarchy of records, this {@code switch} is total: the
     * compiler proves every shape is handled, with no {@code instanceof} ladder
     * and no {@code default} branch to forget.
     */
    static JsonValue redact(JsonValue value, Predicate<String> sensitive) {
        return switch (value) {
            case JsonObject(var members) -> {
                var b = objectBuilder();
                members.forEach((key, val) -> b.put(key,
                        sensitive.test(key) && val instanceof JsonString
                                ? Basic.of("***")
                                : redact(val, sensitive)));
                yield b.build();
            }
            case JsonArray(var elements) -> {
                var b = arrayBuilder();
                elements.forEach(e -> b.add(redact(e, sensitive)));
                yield b.build();
            }
            case Basic<?> leaf -> leaf; // numbers, booleans, nulls, and non-sensitive strings
        };
    }

    @Test
    void deepRedactionIsExhaustiveAndImmutable() throws IOException {
        var doc = Greyson.readValue(Reader.of(EXPORT)).orElseThrow();
        Predicate<String> sensitive = Set.of("password", "token", "secret", "cardNumber")::contains;

        var clean = redact(doc, sensitive);

        assertAll(
                // every sensitive leaf is masked, wherever it sits in the tree
                () -> assertEquals("***", parse("profile/password").requireString(clean)),
                () -> assertEquals("***", parse("payment/methods/[0]/cardNumber").requireString(clean)),
                () -> assertEquals("***", parse("payment/methods/[1]/cardNumber").requireString(clean)),
                () -> assertEquals("***", parse("sessions/[1]/token").requireString(clean)),
                () -> assertEquals("***", parse("metadata/audit/secret").requireString(clean)),
                // non-sensitive data is preserved verbatim
                () -> assertEquals("ada@example.com", parse("profile/email").requireString(clean)),
                () -> assertEquals(3, parse("metadata/version").requireInt(clean)),
                () -> assertEquals("London", parse("profile/addresses/[0]/city").requireString(clean)),
                // and the original document is untouched — redact never mutated it
                () -> assertEquals("hunter2", parse("profile/password").requireString(doc)),
                () -> assertEquals("4111111111111111",
                        parse("payment/methods/[0]/cardNumber").requireString(doc))
        );
    }

    @Test
    void crossCuttingExtractionWithoutASchema() throws IOException {
        var doc = Greyson.readValue(Reader.of(EXPORT)).orElseThrow();

        // every session id across the array — no DTO, no TypeReference
        var sessionIds = parse("sessions").select(all()).apply(doc)
                .flatMap(s -> s.get("id").stream())
                .flatMap(v -> v.string().stream())
                .toList();

        // how many payment methods are cards
        long cards = parse("payment/methods").select(all()).apply(doc)
                .flatMap(m -> m.get("type").stream())
                .flatMap(v -> v.string().stream())
                .filter("card"::equals)
                .count();

        assertAll(
                () -> assertEquals(List.of("s-1", "s-2"), sessionIds),
                () -> assertEquals(2L, cards)
        );
    }

    @Test
    void immutableTargetedUpdate() throws IOException {
        var doc = Greyson.readValue(Reader.of(EXPORT)).orElseThrow();
        var profileBefore = parse("profile").require(doc); // off-path for both edits below

        // bump a nested counter and revoke the first session, both immutably
        var bumped = parse("metadata/version").with(doc, Basic.of(4));
        var revoked = parse("sessions/[0]").without(bumped);

        assertAll(
                () -> assertEquals(4, parse("metadata/version").requireInt(revoked)),
                () -> assertEquals(1, parse("sessions").require(revoked).elements().size()),
                () -> assertEquals("s-2", parse("sessions/[0]/id").requireString(revoked)),
                // the original is intact at every step
                () -> assertEquals(3, parse("metadata/version").requireInt(doc)),
                () -> assertEquals(2, parse("sessions").require(doc).elements().size()),
                // and the untouched "profile" subtree is shared by identity through
                // both edits — the rebuild touches only objects along each path
                () -> assertSame(profileBefore, parse("profile").require(revoked))
        );
    }
}
