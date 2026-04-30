package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.data.JsonArray;
import io.github.ralfspoeth.json.data.JsonObject;
import io.github.ralfspoeth.json.data.JsonValue;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 *
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
     *
     * @return the selector which returns the contents of the {@link JsonValue}
     */
    public static Selector all() {
        return AllSelector.INSTANCE;
    }


    private static final class RangeSelector extends Selector {

        private final int min, max;

        private RangeSelector(int min, int max) {
            this.min = min;
            this.max = (max > min ? max : Integer.MAX_VALUE);
        }

        @Override
        public Stream<JsonValue> apply(JsonValue v) {
            return v instanceof JsonArray(var array) ? IntStream
                                                       .range(min, max)
                                                       .takeWhile(i -> i < array.size())
                                                       .mapToObj(array::get) :
                    Stream.empty();
        }


    }

    private static final class RegexSelector extends Selector {

        private final Pattern regex;

        private RegexSelector(Pattern regex) {
            this.regex = requireNonNull(regex);
        }

        @Override
        public Stream<JsonValue> apply(JsonValue elem) {
            return elem instanceof JsonObject(Map<String, JsonValue> members) ? members
                                                  .entrySet()
                                                  .stream()
                                                  .filter(e -> regex.matcher(e.getKey()).matches())
                                                  .map(Map.Entry::getValue) :
                    Stream.of();
        }

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
    public static Selector range(int startInclusive, int endExclusive) {
        return new RangeSelector(startInclusive, endExclusive);
    }

    /**
     * Create a path which, when applied to a {@link JsonObject},
     * produces a stream of {@link JsonValue}s if its {@link JsonObject#members()}
     * the keys of which match the given regular expression.
     * {@snippet :
     * // given
     * import io.github.ralfspoeth.basix.fn.Functions; var o = new JsonObject(Map.of("a1", Basic.of(1), "a2", Basic.of(2), "b", Basic.of(3)));
     * // when
     * var aN = Selector.regex("a[0-9]"); // a0, a1, a2, ..., a9, not b
     * // then
     * var l = Stream.of(o).flatMap(aN).toList();
     * var c = List.of(Basic.of(1), Basic.of(2));
     * assert Functions.contentsEquals(l, c);
     *}
     *
     * @param regex a regular expression matched against the keys of
     */
    public static Selector regex(Pattern regex) {
        return new RegexSelector(regex);
    }

    /**
     * Same as {@code regex(Pattern.compile(regex));}
     *
     * @param regex the regular expression
     */
    public static Selector regex(String regex) {
        return regex(Pattern.compile(regex));
    }

    /**
     * Mapping function that uses an {@code extractor} for some derived value,
     * and a {@code mapper} which maps the extracted value to the target domain.
     * @param extractor a function that derives some property of the given input
     * @param mapper a function that maps the result of the {@code extractor} into the target domain
     * @return a function
     * @param <T> the type of the resulting stream contents
     * @param <M> an intermediary type
     */
    public <T, M> Function<? super JsonValue, Stream<T>> as(
            Function<? super JsonValue, Optional<? extends M>> extractor,
            Function<? super M, T> mapper) {
        return v -> apply(v).
                flatMap(x -> extractor.apply(x).map(mapper).stream());
    }

    /**
     * Compose this selector with a {@link Pointer}: apply this selector to
     * fan out into a stream, then narrow each element via the pointer. Values
     * for which the pointer does not resolve are dropped from the stream.
     *
     * <p>Dual of {@link Pointer#select(Selector)}: where {@code select} starts
     * from a single value and fans out, {@code point} starts from a stream and
     * narrows each element to a single sub-value.</p>
     *
     * {@snippet :
     * import java.util.stream.Stream;
     * JsonValue users = null; // @replace regex="null;" replacement="..."
     * // From an array of user objects, pull out every "email" that is present.
     * var emails = Selector.all().point(Pointer.self().member("email"));
     * Stream.of(users).flatMap(emails).forEach(System.out::println);
     *}
     *
     * @param p a pointer applied to each value this selector produces
     * @return a stream-shaped function suitable for {@link Stream#flatMap(Function)}
     */
    public Function<? super JsonValue, Stream<JsonValue>> point(Pointer p) {
        return v -> apply(v).flatMap(x -> p.apply(x).stream());
    }
}
