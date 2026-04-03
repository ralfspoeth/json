package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.data.JsonArray;
import io.github.ralfspoeth.json.data.JsonObject;
import io.github.ralfspoeth.json.data.JsonValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * {@code Path}s are inspired by the {@code XPath} querying facility invented
 * with and for XML. Instances are created through the factory method
 * {@link Selector#of(String)} where the argument is first split into components
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
 * Selector p = Selector.of("[2, -1]/#a.*");
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
 * var p = Selector.all().member("a").index(1).regex(Pattern.compile("b.*c")).range(0, 2);
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
 * Selector p = Selector.of("..."); // @replace regex='"..."' replacement="..."
 * JsonArray a = new JsonArray(List.of()); // @replace regex='new JsonArray(List.of())' replacement='...'
 * List<JsonValue> result = a.elements().stream().flatMap(p).toList(); // @highlight substring="flatMap(p)"
 *}
 */
public sealed abstract class Selector implements Function<JsonValue, Stream<JsonValue>> {

    private static final class AllSelector extends Selector {

        private static final AllSelector INSTANCE = new AllSelector();

        @Override
        public Stream<JsonValue> apply(JsonValue jsonValue) {
            return jsonValue instanceof JsonArray(var elements) ?
                    elements.stream() :
                    Stream.of(jsonValue);
        }
    }

    /**
     * Every {@code Selector} starts with this special selector
     * which creates a stream of the elements of a {@link JsonArray}
     * or else the singleton stream of the {@link JsonValue} which it
     * is applied to.
     * @return the selector which returns the contents of the {@link JsonValue}
     */
    public static Selector all() {
        return AllSelector.INSTANCE;
    }

    private static abstract sealed class AbstractSelector extends Selector {
        private final Selector parent;

        protected AbstractSelector(Selector parent) {
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

        abstract AbstractSelector withParent(Selector parent);

        AbstractSelector resolve(AbstractSelector p) {
            if (p.parent instanceof AbstractSelector ap) {
                return ap.resolve(p.withParent(this));
            } else {
                return p.withParent(this);
            }
        }
    }

    private static final class RangeSelector extends AbstractSelector {

        private final int min, max;

        private RangeSelector(int min, int max, Selector parent) {
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
        AbstractSelector withParent(Selector parent) {
            return new RangeSelector(min, max, parent);
        }
    }

    private static final class RegexSelector extends AbstractSelector {

        private final Pattern regex;

        private RegexSelector(String regex, Selector parent) {
            this(Pattern.compile(regex), parent);
        }

        private RegexSelector(Pattern regex, Selector parent) {
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
        AbstractSelector withParent(Selector parent) {
            return new RegexSelector(regex, parent);
        }
    }

    private static final class PointerSelector extends AbstractSelector {
        private final Pointer pointer;

        private PointerSelector(Pointer pointer, Selector parent) {
            super(parent);
            this.pointer = requireNonNull(pointer);
        }

        @Override
        Stream<JsonValue> evalSegment(JsonValue elem) {
            return pointer.apply(elem).stream();
        }

        @Override
        AbstractSelector withParent(Selector parent) {
            return new PointerSelector(pointer, parent);
        }

    }

    private static final Pattern RANGE_PATTERN = Pattern.compile("\\[(\\d+)\\.\\.(-?\\d+)]");

    /**
     * Instantiate a {@link Selector} from a path pattern.
     * The given pattern is first split into parts
     * using {@code '/'}.
     *
     * @param pattern a path pattern
     * @return a path
     */
    public static Selector of(String pattern) {
        var parts = requireNonNull(pattern).split("/");
        Selector prev = all();
        for (String part : parts) {
            var m = RANGE_PATTERN.matcher(part);
            if (m.matches()) {
                prev = new RangeSelector(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), prev);
            } else {
                prev = new RegexSelector(part, prev);
            }
        }
        return prev;
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
    public Selector range(int startInclusive, int endExclusive) {
        return new RangeSelector(startInclusive, endExclusive, this);
    }

    /**
     * Create a path which, when applied to a {@link JsonObject},
     * produces a stream of {@link JsonValue}s if its {@link JsonObject#members()}
     * the keys of which match the given regular expression.
     * {@snippet :
     * // given
     *      * var o = new JsonObject(Map.of("a1", Basic.of(1), "a2", Basic.of(2), "b", Basic.of(3)));
     * // when
     * var p = Selector.regex("a[0-9]"); // a0, a1, a2, ..., a9
     * // then
     * var l = Stream.of(o).flatMap(p).toList();
     * var c = List.of(Basic.of(1), Basic.of(2));
     * assert l.containsAll(c) && c.containsAll(l);
     *}
     *
     * @param regex a regular expression matched against the keys of
     */
    public Selector regex(Pattern regex) {
        return new RegexSelector(regex, this);
    }

    /**
     * Same as {@code regex(Pattern.compile(regex));}
     *
     * @param regex the regular expression
     */
    public Selector regex(String regex) {
        return regex(Pattern.compile(regex));
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
     * var root = Selector.all();
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
    public Selector resolve(Selector p) {
        if (this instanceof AbstractSelector tp && p instanceof AbstractSelector ap) {
            return tp.resolve(ap);
        } else if (p instanceof AbstractSelector ap) {
            assert this instanceof AllSelector;
            return ap;
        } else {
            return this;
        }
    }

    /**
     * Append a {@link Pointer}
     *
     * @param p a pointer
     * @return a new selector
     */
    public Selector resolve(Pointer p) {
        return new PointerSelector(p, this);
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

    public <T, M> Function<? super JsonValue, Stream<T>> as(
            Function<? super JsonValue, Optional<? extends M>> extractor,
            Function<? super M, T> mapper) {
        return v -> apply(v).
                flatMap(x -> extractor.apply(x).map(mapper).stream());
    }
}
