package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.data.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * {@code Path}s are inspired by the {@code XPath} querying facility invented
 * with and for XML. Instances are created through the factory method
 * {@link Pointer#parse(String)} where the argument is first split into components
 * using the slash {@code /} as separator and then each component matches a
 * pattern of the following form:
 * <ul>
 * <li>{@code [n]} where {@code n} is an integer denoting the index of the
 * element in JSON array. Negative values of {@code n} point to the n-th element
 * from the end of the array.</li>
 * <li>{@code [a..b]} where {@code a} is non-negative integer and {@code b} any
 * integer; resolves to the range from {@code a} inclusively to {@code b}
 * exclusively and may be applied to JSON arrays. A negative value of {@code b}
 * is interpreted such that there is no upper bound; that is: {@code [0..-1]}
 * matches all elements of a JSON array
 * </li>
 * <li>{@code #regex} where {@code regex} is a regular expression; the slash may
 * not be included in this expression. The path matches every member of a JSON
 * object, the name part of which matches the given regular expression.
 * </li>
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
 * Pointer p = Pointer.parse("[2, -1]/#a.*");
 *
 * // then
 * List<JsonValue> result = Greyson.readValue(given).map(p).stream().toList();
 * assert result.size() == 3; // three JSON objects...
 * assert result.getFirst() == JsonBoolean.TRUE; // the "aa" member of the third object
 * assert result.get(1).equals(Basic.of(2)); // the "ab" member of the fourth
 * assert result.getLast().equals(Basic.of(3)); // the "ac" member of the fifth
 *}
 * <p>
 * The second approach to constructing paths is through the fluent API, as in
 * {@snippet :
 * var p = Pointer.self().member("a").index(1).regex(Pattern.compile("b.*c")).range(0, 2);
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

    private static final Pattern INDEX_PATTERN = Pattern.compile("\\[(-?\\d+)]");

    /**
     * Instantiate a {@link Pointer} from a text.
     * The text is first split into parts
     * using {@code '/'}. Integers are passed into
     * {@link #index(int)}, all others as literal strings
     * into {@link #member(String)}.
     * Example:
     * {@snippet :
     * var p = Pointer.parse("a/2/b/3");
     * var q = Pointer.self().member("a").index(2).member("b").index(3);
     * // p and q are equivalent
     * }
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
}
