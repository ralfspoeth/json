package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.data.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * {@link Pointer}s are inspired by the {@code XPath} querying facility invented
 * with and for XML. Instances are created through the factory method
 * {@link Pointer#parse(String)} where the argument is first split into components
 * using the slash {@code /} as separator and then each component matches a
 * pattern of the following form:
 * <ul>
 * <li>{@code [n]} where {@code n} is an integer denoting the index of the
 * element in JSON array. Negative values of {@code n} point to the n-th element
 * from the end of the array.</li>
 * <li>{@code name} where {@code name} is just the literal member name of a JSON
 * object</li>
 * </ul>
 * Example:
 * <p>
 * {@snippet :
 * import io.github.ralfspoeth.json.Greyson;
 * import io.github.ralfspoeth.json.data.JsonValue;
 * import io.github.ralfspoeth.json.data.JsonBoolean;
 *
 * import java.util.List;
 *
 * // given be a JSON array of five elements, two numbers and three JSON objects
 * var given = """
 *       [1, 2, {"aa": true}, {"xy": 5, "ab": 2}, {"ac": 3}]
 *       """;
 *
 * // when p matches any element beginning with the third
 * // and then each member starts with 'a'
 * Pointer p = Pointer.parse("2/aa");
 *
 * // then
 * List<JsonValue> result = Greyson.readValue(given).flatMap(p).stream().toList();
 * assert result.size() == 1; // three JSON objects...
 * assert result.getFirst() == JsonBoolean.TRUE; // the "aa" member of the third object
 *}
 * <p>
 * The second approach to constructing paths is through the fluent API, as in
 * {@snippet :
 * var p = Pointer.self().index(2).member("a");
 *}
 * <p>
 * The class implements {@link Function} such that it may be used in stream
 * pipelines easily as in
 * <p>
 * {@snippet :
 * import java.util.List;
 * import io.github.ralfspoeth.greyson.*;
 * import io.github.ralfspoeth.json.data.JsonArray;
 * import io.github.ralfspoeth.json.data.JsonValue;
 * Pointer p = Pointer.parse("..."); // @replace regex='"..."' replacement="..."
 * JsonArray a = new JsonArray(List.of()); // @replace regex='new JsonArray(List.of())' replacement='...'
 * List<JsonValue> result = a.elements().stream().flatMap(p).toList(); // @highlight substring="flatMap(p)"
 *}
 */
public sealed abstract class Pointer implements Function<JsonValue, Optional<JsonValue>> {

    private static final class Self extends Pointer {

        private static final Self INSTANCE = new Self();

        @Override
        public Optional<JsonValue> apply(JsonValue jsonValue) {
            return Optional.of(jsonValue);
        }
    }

    /**
     * Always start with a root path, which matches
     * the given argument and returns it in a fresh {@link Stream}.
     */
    public static Pointer self() {
        return Self.INSTANCE;
    }

    private static abstract sealed class AbstractPointer extends Pointer {
        private final Pointer parent;

        protected AbstractPointer(Pointer parent) {
            this.parent = requireNonNull(parent);
        }

        abstract Optional<JsonValue> evalSegment(JsonValue elem);

        /**
         * To be used with {@link Stream#flatMap(Function)} in a stream
         * pipeline.
         *
         * @param value a JSON element
         * @return all children of this path applied to the given root
         */
        @Override
        public Optional<JsonValue> apply(JsonValue value) {
            return parent.apply(value).flatMap(this::evalSegment);
        }

        abstract AbstractPointer withParent(Pointer parent);

        AbstractPointer resolve(AbstractPointer p) {
            if (p.parent instanceof AbstractPointer ap) {
                return ap.resolve(p.withParent(this));
            } else {
                return p.withParent(this);
            }
        }
    }

    private static final class MemberPointer extends AbstractPointer {

        private final String memberName;

        private MemberPointer(String memberName, Pointer parent) {
            super(parent);
            this.memberName = requireNonNull(memberName);
        }

        @Override
        Optional<JsonValue> evalSegment(JsonValue elem) {
            return elem instanceof JsonObject(var members) ?
                    Optional.ofNullable(members.get(memberName)) :
                    Optional.empty();
        }

        @Override
        AbstractPointer withParent(Pointer parent) {
            return new MemberPointer(memberName, parent);
        }
    }

    private static final class IndexPointer extends AbstractPointer {
        private final int index;

        private IndexPointer(int index, Pointer parent) {
            super(parent);
            this.index = index;
        }

        @Override
        Optional<JsonValue> evalSegment(JsonValue elem) {
            if (elem instanceof JsonArray(var elements)) {
                if (index >= 0 && index < elements.size()) return Optional.of(elements.get(index));
                else if (index < 0 && 0 <= elements.size() + index)
                    return Optional.of(elements.get(elements.size() + index));
                else return Optional.empty();
            } else {
                return Optional.empty();
            }
        }

        @Override
        AbstractPointer withParent(Pointer parent) {
            return new IndexPointer(index, parent);
        }
    }

    /**
     * A pointer segment that matches object members by regular expression
     * and yields the value associated with the lexicographically smallest
     * matching key. The name reflects its semantics: of all keys that satisfy
     * the pattern, the {@code First} one in sorted order wins. Use
     * {@link Selector#regex(Pattern)} when you need every match instead.
     */
    private static final class First extends AbstractPointer {
        private final Pattern pattern;

        private First(Pattern pattern, Pointer parent) {
            super(parent);
            this.pattern = requireNonNull(pattern);
        }

        @Override
        Optional<JsonValue> evalSegment(JsonValue elem) {
            if (!(elem instanceof JsonObject(var members))) {
                return Optional.empty();
            }
            // Sorting by key makes the result deterministic regardless of map
            // iteration order. For {@code "bal(ance)?"} against
            // {@code {"bal": ..., "balance": ...}} the shorter key wins.
            return members.entrySet().stream()
                    .filter(e -> pattern.matcher(e.getKey()).matches())
                    .min(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue);
        }

        @Override
        AbstractPointer withParent(Pointer parent) {
            return new First(pattern, parent);
        }
    }

    /**
     * A pointer segment that follows the RFC 6901 dispatch rule: the same
     * token resolves as a member name when applied to a JSON object and as
     * a non-negative array index when applied to a JSON array. Built only
     * by {@link #fromJsonPointer(String)}.
     */
    private static final class JsonPointerSegment extends AbstractPointer {
        private final String token;

        private JsonPointerSegment(String token, Pointer parent) {
            super(parent);
            this.token = requireNonNull(token);
        }

        @Override
        Optional<JsonValue> evalSegment(JsonValue elem) {
            return switch (elem) {
                case JsonObject(var members) -> Optional.ofNullable(members.get(token));
                case JsonArray(var elements) -> {
                    int idx = parseRfc6901Index(token);
                    yield (idx >= 0 && idx < elements.size())
                            ? Optional.of(elements.get(idx))
                            : Optional.empty();
                }
                default -> Optional.empty();
            };
        }

        // RFC 6901 array-index ABNF: '0' or [1-9][0-9]* — no signs, no leading
        // zeros. Returns -1 for any token that does not match (including the
        // RFC's '-' "after-last" marker, which has no read-side meaning here).
        private static int parseRfc6901Index(String token) {
            int len = token.length();
            if (len == 0) return -1;
            if (len > 1 && token.charAt(0) == '0') return -1;
            int idx = 0;
            for (int i = 0; i < len; i++) {
                char c = token.charAt(i);
                if (c < '0' || c > '9') return -1;
                idx = idx * 10 + (c - '0');
                if (idx < 0) return -1; // overflow
            }
            return idx;
        }

        @Override
        AbstractPointer withParent(Pointer parent) {
            return new JsonPointerSegment(token, parent);
        }
    }

    private static final Pattern INDEX_PATTERN = Pattern.compile("\\[(-?\\d+)]");

    /**
     * Instantiate a {@link Pointer} from a text.
     * The text is split into parts on {@code '/'}; each part is then dispatched
     * by syntax:
     * <ul>
     *   <li>{@code [n]} (with an optional leading {@code -} for indices counted
     *       from the end of the array) is passed to {@link #index(int)};</li>
     *   <li>parts beginning with {@code '#'} pass the remainder to
     *       {@link #regex(String)}, matching object members whose key satisfies
     *       the regular expression — when several keys match, the
     *       lexicographically smallest wins;</li>
     *   <li>everything else is treated as a literal member name and passed to
     *       {@link #member(String)}.</li>
     * </ul>
     * Example:
     * {@snippet :
     * var p = parse("a/[2]/#b.*");
     * var q = self().member("a").index(2).regex("b.*");
     * // p and q are equivalent
     *}
     * Bare digit segments are member names, not array indices. Use {@code [n]}
     * for indices; if you need an integer-named member, just write the integer
     * (e.g. {@code "1"}) or call {@link #member(String)} directly.
     *
     * @param text a pointer text
     * @return a pointer
     */
    public static Pointer parse(String text) {
        var parts = requireNonNull(text).split("/");
        Pointer prev = self();
        for (String part : parts) {
            var im = INDEX_PATTERN.matcher(part);
            if (im.matches()) {
                var index = Integer.parseInt(im.group(1));
                prev = new IndexPointer(index, prev);
            } else if (part.startsWith("#")) {
                prev = new First(Pattern.compile(part.substring(1)), prev);
            } else {
                prev = new MemberPointer(part, prev);
            }
        }
        return prev;
    }

    /**
     * Create a path element that, if applied to some {@link JsonObject},
     * returns the associated {@link JsonObject#members()} key.
     *
     * @param memberName the name of the member
     */
    public Pointer member(String memberName) {
        return new MemberPointer(memberName, this);
    }

    /**
     * Create a path that picks the element at the given index,
     * or, if negative, at the index of the reversed array,
     * given that it is applied to a JSON array.
     * {@snippet :
     * // given
     * JsonArray a = new JsonArray(Basic.of(1), Basic.of(2), Basic.of(3));
     * // when
     * var first = Pointer.index(0);
     * var last = Pointer.index(-1);
     * var end = Pointer.index(a.size()-1);
     * // then
     * assert first.apply(a).findFirst().orElseThrow().equals(Basic.of(1));
     * assert last.apply(a).findFirst().orElseThrow().equals(Basic.of(3));
     * assert end.apply(a).findFirst().orElseThrow().equals(Basic.of(3));
     *}
     *
     * @param index the index
     */
    public Pointer index(int index) {
        return new IndexPointer(index, this);
    }

    /**
     * Create a pointer segment that matches object members by regular expression.
     * If multiple members satisfy the pattern, the value of the lexicographically
     * smallest matching key is returned — see {@code First} for the rationale.
     * Use {@link Selector#regex(Pattern)} when you need every match.
     *
     * @param pattern the regex pattern, may not be {@code null}
     * @return a pointer
     */
    public Pointer regex(Pattern pattern) {
        return new First(pattern, this);
    }

    /**
     * Same as {@code regex(Pattern.compile(pattern))}.
     *
     * @param pattern the regex pattern, may not be {@code null}
     * @return a pointer
     */
    public Pointer regex(String pattern) {
        return regex(Pattern.compile(pattern));
    }

    /**
     * Build a pointer from an
     * <a href="https://datatracker.ietf.org/doc/html/rfc6901">RFC 6901</a>
     * JSON Pointer expression.
     *
     * <p>The empty string returns {@link #self()}. Otherwise the expression
     * must start with {@code '/'}. The remaining tokens are split on
     * {@code '/'}; each token is then unescaped according to the spec
     * ({@code ~1} → {@code /}, {@code ~0} → {@code ~}, applied in that order)
     * and resolved dynamically: against a {@link JsonObject} the token is
     * looked up as a member name; against a {@link JsonArray} the token must
     * be a non-negative decimal integer (no leading zeros, no sign) and
     * indexes the array.</p>
     *
     * <p>Note that this dispatch differs from {@link #parse(String)}, which
     * decides member-vs-index statically from the syntax. The two parsers
     * exist side by side: use {@code parse} for Greyson's native syntax with
     * its {@code [n]} index marker; use {@code fromJsonPointer} to interoperate
     * with anything that emits RFC 6901 strings (JSON Patch, OpenAPI
     * {@code $ref}, JSON Schema, …).</p>
     *
     * {@snippet :
     * import io.github.ralfspoeth.json.Greyson;
     * var json = """
     *       {"a/b": [10, 20, 30], "m~n": "tilde"}
     *       """;
     * var slash = Pointer.fromJsonPointer("/a~1b/2");
     * var tilde = Pointer.fromJsonPointer("/m~0n");
     * assert Greyson.readValue(java.io.Reader.of(json))
     *     .flatMap(slash).flatMap(JsonValue::decimal).orElseThrow().intValue() == 30;
     * assert Greyson.readValue(java.io.Reader.of(json))
     *     .flatMap(tilde).flatMap(JsonValue::string).orElseThrow().equals("tilde");
     *}
     *
     * @param pointer an RFC 6901 JSON Pointer string, may not be {@code null}
     * @return a pointer
     * @throws IllegalArgumentException if {@code pointer} is non-empty and does
     *                                  not start with {@code '/'}
     */
    public static Pointer fromJsonPointer(String pointer) {
        requireNonNull(pointer);
        if (pointer.isEmpty()) {
            return self();
        }
        if (pointer.charAt(0) != '/') {
            throw new IllegalArgumentException(
                    "JSON Pointer must be empty or start with '/': " + pointer);
        }
        // {@code limit = -1} preserves trailing empty tokens, so a pointer like
        // "/foo/" produces {"foo", ""} rather than {"foo"} — the empty string
        // is a valid member name in RFC 6901.
        String[] tokens = pointer.substring(1).split("/", -1);
        Pointer prev = self();
        for (String token : tokens) {
            prev = new JsonPointerSegment(unescapeRfc6901(token), prev);
        }
        return prev;
    }

    // RFC 6901 §4: replace {@code ~1} first, then {@code ~0}. Order matters so
    // that {@code ~01} (the literal {@code ~1} encoded) does not get mangled
    // into a slash.
    private static String unescapeRfc6901(String token) {
        if (token.indexOf('~') < 0) return token;
        return token.replace("~1", "/").replace("~0", "~");
    }

    /**
     * Resolve the given pointer {@code p} relative to this pointer.
     * {@snippet :
     * // given
     * var json = """
     *       [{"a":1}, {"a":2, "b":3}, 4, null]
     *       """;
     * // when
     * var self = Pointer.self();
     * var index1 = self.index(1);
     * var mem_a = self.member("a");
     * // when
     * var p1a = index1.resolve(mem_a);
     * // then
     * assert Greyson.readValue(Reader.of(json))
     *     .flatMap(p1a1)
     *     .orElseThrow()
     *     .equals(Basic.of(2));
     *}
     *
     * @param p another pointer, may not be {@code null}
     * @return a new pointer combining {@code this} and {@code p}
     */
    public Pointer resolve(Pointer p) {
        if (this instanceof AbstractPointer tp && p instanceof AbstractPointer ap) {
            return tp.resolve(ap);
        } else if (p instanceof AbstractPointer ap) {
            assert this instanceof Self;
            return ap;
        } else {
            return this;
        }
    }

    // Conversion convention: primitive payloads return OptionalInt/OptionalLong/OptionalDouble; reference types return Optional<T>.

    /**
     * Search the first {@link JsonNumber} if found by this and return it as
     * {@code OptionalInt}; otherwise return {@link OptionalInt#empty()}.
     *
     * @param v the value, may not be {@code null}
     * @return the int value if found wrapped in {@link OptionalInt}, empty otherwise
     */
    public OptionalInt intValue(JsonValue v) {
        return apply(v).flatMap(JsonValue::decimal)
                .map(d -> OptionalInt.of(d.intValue()))
                .orElse(OptionalInt.empty());
    }

    /**
     * Search the first {@link JsonNumber} if found by this and return it as
     * {@code OptionalLong}; otherwise return {@link OptionalLong#empty()}.
     *
     * @param v the value, may not be {@code null}
     * @return the long value if found wrapped in {@link OptionalLong}, empty otherwise
     */
    public OptionalLong longValue(JsonValue v) {
        return apply(v).flatMap(JsonValue::decimal)
                .map(d -> OptionalLong.of(d.longValue()))
                .orElse(OptionalLong.empty());
    }

    /**
     * Search the first {@link JsonNumber} if found by this and return it as
     * {@code OptionalInt} using {@link BigDecimal#intValueExact()};
     * otherwise return {@link OptionalInt#empty()}.
     *
     * @param v the value, may not be {@code null}
     * @return the int value if found wrapped in {@link OptionalInt}, empty otherwise
     */
    public OptionalInt intValueExact(JsonValue v) {
        return apply(v).flatMap(JsonValue::decimal)
                .map(d -> OptionalInt.of(d.intValueExact()))
                .orElse(OptionalInt.empty());
    }

    /**
     * Search the first {@link JsonNumber} if found by this and return it as
     * {@code OptionalLong} using {@link BigDecimal#longValueExact()};
     * otherwise return {@link OptionalLong#empty()}.
     *
     * @param v the value, may not be {@code null}
     * @return the long value if found wrapped in {@link OptionalLong}, empty otherwise
     */
    public OptionalLong longValueExact(JsonValue v) {
        return apply(v).flatMap(JsonValue::decimal)
                .map(d -> OptionalLong.of(d.longValueExact()))
                .orElse(OptionalLong.empty());
    }

    /**
     * Search the first {@link JsonNumber} if found by this and return it as
     * {@code OptionalDouble}; otherwise return {@link OptionalDouble#empty()}.
     *
     * @param v the value, may not be {@code null}
     * @return the double value if found wrapped in {@link OptionalDouble}, empty otherwise
     */
    public OptionalDouble doubleValue(JsonValue v) {
        return apply(v).flatMap(JsonValue::decimal)
                .map(d -> OptionalDouble.of(d.doubleValue()))
                .orElse(OptionalDouble.empty());
    }

    /**
     * Search the first {@link JsonBoolean} if found by this and return its value.
     *
     * @param v the value, may not be {@code null}
     * @return the boolean value if found, empty otherwise
     */
    public Optional<Boolean> booleanValue(JsonValue v) {
        return apply(v).flatMap(JsonValue::bool);
    }

    /**
     * Search the first {@link JsonString} if found by this and return its value.
     *
     * @param v the value, may not be {@code null}
     * @return the string value if found, empty otherwise
     */
    public Optional<String> stringValue(JsonValue v) {
        return apply(v).flatMap(JsonValue::string);
    }

    /**
     * Create a function to be used with {@link Optional#flatMap(Function)} which
     * first extracts an optional value from the {@link JsonValue}, as with {@link JsonValue#string()},
     * and maps the payload of the optional value using the {@code mapper} function.
     * Example:
     * {@snippet :
     * import java.time.LocalDate;
     * import io.github.ralfspoeth.json.Greyson;
     * String src = "[\"2025-05-05\"]";
     * Pointer p = Pointer.self().index(0);
     * var ld = Greyson.readValue(Reader.of(src))
     *     .flatMap(p.as(JsonValue::string, LocalDate::parse))
     *     .orElseThrow();
     * assert ld.equals(LocalDate.of(2025, 5, 5));
     *}
     *
     * @param extractor an extraction function, returning an optional value
     * @param mapper    a mapper function applied to the payload of the extractor
     * @param <T>       the return type of the mapper
     * @param <M>       some intermediary type
     * @return a function to be used with {@link Optional#flatMap(Function)}
     */
    public <T, M> Function<? super JsonValue, Optional<? extends T>> as(
            Function<? super JsonValue, Optional<? extends M>> extractor,
            Function<? super M, T> mapper) {
        return v -> apply(v).flatMap(extractor).map(mapper);
    }

    /**
     * Compose this pointer with a {@link Selector}: navigate via this pointer
     * first, then apply the selector to whatever the pointer resolves to. The
     * returned function is empty when the pointer does not resolve.
     *
     * <p>Dual of {@link Selector#point(Pointer)}: where {@code point} starts
     * from a stream and narrows each element to a single sub-value,
     * {@code select} starts from a single value and fans out.</p>
     *
     * {@snippet :
     * import java.util.stream.Stream;
     * JsonValue doc = null; // @replace regex="null;" replacement="..."
     * // Navigate to "data/users" and stream every element of the array there.
     * var users = Pointer.parse("data/users").select(Selector.all());
     * Stream.of(doc).flatMap(users).forEach(System.out::println);
     *}
     *
     * @param selector a selector applied to whatever this pointer resolves to
     * @return a stream-shaped function suitable for {@link Stream#flatMap(Function)}
     */
    public Function<JsonValue, Stream<JsonValue>> select(Selector selector) {
        return v -> apply(v).stream().flatMap(selector);
    }
}
