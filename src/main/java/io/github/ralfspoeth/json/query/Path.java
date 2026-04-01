package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.data.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * {@code Path}s are inspired by the {@code XPath} querying facility invented
 * with and for XML. Instances are created through the factory method
 * {@link Path#of(String)} where the argument is first split into components
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
 * Path p = Path.of("[2, -1]/#a.*");
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
 * var p = Path.root().member("a").index(1).regex(Pattern.compile("b.*c")).range(0, 2);
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
 * Path p = Path.of("..."); // @replace regex='"..."' replacement="..."
 * JsonArray a = new JsonArray(List.of()); // @replace regex='new JsonArray(List.of())' replacement='...'
 * List<JsonValue> result = a.elements().stream().flatMap(p).toList(); // @highlight substring="flatMap(p)"
 *}
 */
public sealed abstract class Path implements Function<JsonValue, Stream<JsonValue>> {

    private static final class RootPath extends Path {

        private static final RootPath ROOT = new RootPath();

        @Override
        public Stream<JsonValue> apply(JsonValue jsonValue) {
            return Stream.of(jsonValue);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof RootPath;
        }
    }

    /**
     * Always start with a root path, which matches
     * the given argument and returns it in a fresh {@link Stream}.
     */
    public static Path root() {
        return RootPath.ROOT;
    }

    private static abstract sealed class AbstractPath extends Path {
        private final Path parent;

        protected Path parent() {
            return parent;
        }

        protected AbstractPath(Path parent) {
            this.parent = requireNonNull(parent);
        }

        abstract Stream<JsonValue> evalSegment(JsonValue elem);

        /**
         * To be used with {@link Stream#flatMap(Function)} in a stream
         * pipeline.
         *
         * @param value a JSON element
         * @return all children of this path applied to the given root
         */
        @Override
        public Stream<JsonValue> apply(JsonValue value) {
            return parent.apply(value).flatMap(this::evalSegment);
        }

        abstract AbstractPath withParent(Path parent);

        AbstractPath resolve(AbstractPath p) {
            if (p.parent instanceof AbstractPath ap) {
                return ap.resolve(p.withParent(this));
            } else {
                return p.withParent(this);
            }
        }
    }

    private static final class MemberPath extends AbstractPath {

        private final String memberName;

        private MemberPath(String memberName, Path parent) {
            super(parent);
            this.memberName = requireNonNull(memberName);
        }

        @Override
        Stream<JsonValue> evalSegment(JsonValue elem) {
            return elem instanceof JsonObject(var members) && members.containsKey(memberName) ?
                    Stream.of(members.get(memberName)) :
                    Stream.of();
        }

        @Override
        AbstractPath withParent(Path parent) {
            return new MemberPath(memberName, parent);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof MemberPath mp && memberName.equals(mp.memberName) && parent().equals(mp.parent());
        }
    }

    private static final class IndexPath extends AbstractPath {
        private final int index;

        private IndexPath(int index, Path parent) {
            super(parent);
            this.index = index;
        }

        @Override
        Stream<JsonValue> evalSegment(JsonValue elem) {
            if (elem instanceof JsonArray(var elements)) {
                if (index >= 0 && index < elements.size()) return Stream.of(elements.get(index));
                else if (index < 0 && 0 <= elements.size() + index)
                    return Stream.of(elements.get(elements.size() + index));
                else return Stream.of();
            } else {
                return Stream.of();
            }
        }

        @Override
        AbstractPath withParent(Path parent) {
            return new IndexPath(index, parent);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof IndexPath ip && index == ip.index && parent().equals(ip.parent());
        }
    }

    private static final class RangePath extends AbstractPath {

        private final int min, max;

        private RangePath(int min, int max, Path parent) {
            super(parent);
            this.min = min;
            this.max = (max > 0 ? max : Integer.MAX_VALUE);
        }

        private Stream<JsonValue> evalArray(List<JsonValue> array) {
            return IntStream.range(min, max)
                    .takeWhile(i -> i < array.size())
                    .mapToObj(array::get);
        }

        @Override
        Stream<JsonValue> evalSegment(JsonValue elem) {
            return elem instanceof JsonArray(var elements) ? evalArray(elements) : Stream.of();
        }

        @Override
        AbstractPath withParent(Path parent) {
            return new RangePath(min, max, parent);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof RangePath rp && min == rp.min && max == rp.max && parent().equals(rp.parent());
        }
    }

    private static final class RegexPath extends AbstractPath {

        private final Pattern regex;

        private RegexPath(String regex, Path parent) {
            this(Pattern.compile(regex), parent);
        }

        private RegexPath(Pattern regex, Path parent) {
            super(parent);
            this.regex = requireNonNull(regex);
        }

        @Override
        Stream<JsonValue> evalSegment(JsonValue elem) {
            return elem instanceof JsonObject o ? evalObject(o) : Stream.of();
        }

        Stream<JsonValue> evalObject(JsonObject o) {
            return o.members()
                    .entrySet()
                    .stream()
                    .filter(e -> regex.matcher(e.getKey()).matches())
                    .map(Map.Entry::getValue);
        }

        @Override
        AbstractPath withParent(Path parent) {
            return new RegexPath(regex, parent);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof RegexPath rp && regex.equals(rp.regex) && parent().equals(rp.parent());
        }
    }

    private static final Pattern INDEX_PATTERN = Pattern.compile("\\[(-?\\d+)]");

    private static final Pattern RANGE_PATTERN = Pattern.compile("\\[(\\d+)\\.\\.(-?\\d+)]");

    /**
     * Instantiate a {@link Path} from a path pattern.
     * The given pattern is first split into parts
     * using {@code '/'}.
     *
     * @param pattern a path pattern
     * @return a path
     */
    public static Path of(String pattern) {
        var parts = requireNonNull(pattern).split("/");
        Path prev = root();
        for (String part : parts) {
            var im = INDEX_PATTERN.matcher(part);
            if (im.matches()) {
                var index = Integer.parseInt(im.group(1));
                prev = new IndexPath(index, prev);
            } else {
                var m = RANGE_PATTERN.matcher(part);
                if (m.matches()) {
                    prev = new RangePath(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), prev);
                } else if (part.startsWith("#")) {
                    prev = new RegexPath(part.substring(1), prev);
                } else {
                    prev = new MemberPath(part, prev);
                }
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
    public Path member(String memberName) {
        return new MemberPath(memberName, this);
    }

    /**
     * Create a path that picks the element at the given index,
     * or, if negative, at the index of the reversed array,
     * given that it is applied to a JSON array.
     * {@snippet :
     * // given
     * JsonArray a = new JsonArray(Basic.of(1), Basic.of(2), Basic.of(3));
     * // when
     * var first = Path.index(0);
     * var last = Path.index(-1);
     * var end = Path.index(a.size()-1);
     * // then
     * assert first.apply(a).findFirst().orElseThrow().equals(Basic.of(1));
     * assert last.apply(a).findFirst().orElseThrow().equals(Basic.of(3));
     * assert end.apply(a).findFirst().orElseThrow().equals(Basic.of(3));
     *}
     *
     * @param index the index
     */
    public Path index(int index) {
        return new IndexPath(index, this);
    }

    /**
     * Create a path that, given a JSON array {@code a},
     * returns a stream of its elements from index {@code startInclusive}
     * to {@code endExclusive} exclusively.
     * If {@code endExclusive} is negative, it is replaced by {@link Integer#MAX_VALUE}.
     *
     * @param startInclusive the first index, inclusively
     * @param endExclusive   the last index, exclusively
     */
    public Path range(int startInclusive, int endExclusive) {
        return new RangePath(startInclusive, endExclusive, this);
    }

    /**
     * Create a path which, when applied to a {@link JsonObject},
     * produces a stream of {@link JsonValue}s if its {@link JsonObject#members()}
     * the keys of which match the given regular expression.
     * {@snippet :
     * // given
     * import java.util.HashSet;
     * var o = new JsonObject(Map.of("a1", Basic.of(1), "a2", Basic.of(2), "b", Basic.of(3)));
     * // when
     * var p = Path.regex("a[0-9]"); // a0, a1, a2, ..., a9
     * // then
     * var l = Stream.of(o).flatMap(p).toList();
     * var c = List.of(Basic.of(1), Basic.of(2));
     * assert l.containsAll(c) && c.containsAll(l);
     *}
     *
     * @param regex a regular expression matched against the keys of
     */
    public Path regex(Pattern regex) {
        return new RegexPath(regex, this);
    }

    /**
     * Same as {@code regex(Pattern.compile(regex));}
     * @param regex the regular expression
     */
    public Path regex(String regex) {
        return new RegexPath(Pattern.compile(regex), this);
    }

    /**
     * Resolve the given path {@code p} against this path.
     * {@snippet :
     * // given
     * import io.github.ralfspoeth.json.data.JsonNull;
     * var value = new JsonArray(
     *         new JsonObject(Map.of("a", Basic.of(1))),
     *         new JsonObject(Map.of("a", Basic.of(2), "b", Basic.of(3))),
     *         Basic.of(4),
     *         JsonNull.INSTANCE
     *         );
     * var root = Path.root();
     * var theFirstTwo = root.range(0, 2);
     * var a = root.member("a");
     * // when
     * var aRelToTheFirstTwo = theFirstTwo.resolve(a);
     * // then
     * assert Stream.of(value).flatMap(aRelToTheFirstTwo).toList().equals(List.of(Basic.of(1), Basic.of(2)));
     *}
     *
     * @param p the given path
     */
    public Path resolve(Path p) {
        if (this instanceof AbstractPath tp && p instanceof AbstractPath ap) {
            return tp.resolve(ap);
        } else if (p instanceof AbstractPath ap) {
            assert this instanceof RootPath;
            return ap;
        } else {
            return this;
        }
    }

    /**
     * Turns this into a function with return type {@link Optional<JsonValue>}
     * rather than {@link Stream<JsonValue>}; intended to be used with
     * {@link Optional#flatMap(Function)}.
     *
     * @return the same as {@code this.apply(value).findFirst()}
     */
    public Function<? super JsonValue, Optional<? extends JsonValue>> first() {
        return andThen(Stream::findFirst);
    }

    /**
     * Search the first {@link JsonNumber} if found by this and return it as
     * {@code OptionalInt}; otherwise return {@link OptionalInt#empty()}.
     *
     * @param v the value, may not be {@code null}
     * @return the int value if found wrapped in {@link OptionalInt}, empty otherwise
     */
    public OptionalInt intValue(JsonValue v) {
        return first().apply(v).flatMap(JsonValue::decimal)
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
        return first().apply(v).flatMap(JsonValue::decimal)
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
        return first().apply(v).flatMap(JsonValue::decimal)
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
        return first().apply(v).flatMap(JsonValue::decimal)
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
        return first().apply(v).flatMap(JsonValue::decimal)
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
        return first().apply(v).flatMap(JsonValue::bool);
    }

    /**
     * Search the first {@link JsonString} if found by this and return its value.
     *
     * @param v the value, may not be {@code null}
     * @return the string value if found, empty otherwise
     */
    public Optional<String> stringValue(JsonValue v) {
        return first().apply(v).flatMap(JsonValue::string);
    }

    /**
     * Create a function to be used with {@link Optional#flatMap(Function)}
     * which takes a function that maps a {@link JsonValue} to an {@link Optional}.
     *
     * @param f the function
     * @param <T> the return type of the function
     * @return a function to be used with {@link Optional#flatMap(Function)}
     */
    public <T> Function<? super JsonValue, Optional<T>> first(Function<? super JsonValue, Optional<T>> f) {
        return first().andThen(r -> r.flatMap(f));
    }

    /**
     * Create a function to be used with {@link Optional#flatMap(Function)}
     * which extracts an optional value from the {@link JsonValue},
     * maybe through {@link JsonValue#decimal()}, and then applies the
     * {@code mapper} function to the payload.
     *
     * @param extractor an extraction function applied to a JSON value
     * @param mapper a mapper function applied to the payload of the extractor
     * @return a function to be used with {@link Optional#flatMap(Function)}
     * @param <T> the return type of the mapper
     * @param <M> some intermediary type
     */
    public <T, M> Function<? super JsonValue, Optional<T>> first(
            Function<? super JsonValue, Optional<? extends M>> extractor,
            Function<? super M, T> mapper) {
        return v -> first().apply(v).flatMap(extractor).map(mapper);
    }


    /**
     * Create a function to be used with {@link Stream#flatMap(Function)} which
     * first extracts an optional value from the {@link JsonValue}, as with {@link JsonValue#string()},
     * and maps the payload of the optional value using the {@code mapper} function.
     * Example:
     * {@snippet :
     * import java.time.LocalDate;
     * import io.github.ralfspoeth.json.Greyson;
     * String src = "[\"2025-05-05\"]";
     * Path p = Path.root().index(0);
     * var ld = Greyson.readValue(Reader.of(src)).stream().flatMap(p.as(JsonValue::string, LocalDate::parse)).toList();
     * assert ld.size() == 1 && ld.getFirst().equals(LocalDate.of(2025, 5, 5));
     *}
     * @param extractor an extraction function, returning an optional value
     * @param mapper a mapper function applied to the payload of the extractor
     * @return a function to be used with {@link Stream#flatMap(Function)}
     * @param <T> the return type of the mapper
     * @param <M> some intermediary type
     */
    public <T, M> Function<? super JsonValue, Stream<? extends T>> as(
            Function<? super JsonValue, Optional<? extends M>> extractor,
            Function<? super M, T> mapper) {
        return v -> apply(v).flatMap(extractor
                .andThen(r -> r.map(mapper))
                .andThen(Optional::stream)
        );
    }
}
