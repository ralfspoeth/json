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
 * import io.github.ralfspoeth.json.data.JsonValue;
 * import io.github.ralfspoeth.json.data.JsonBoolean;
 * import io.github.ralfspoeth.json.io.JsonReader;
 *
 * import java.util.List;
 *
 * // given be a JSON array of five elements, two numbers and three JSON objects
 * var given = """
 *       [1, 2, {"aa": true}, {"xy": 5, "ab": 2}, {"ac": 3}]
 *       """;
 * var elem = JsonReader.readValue(given);
 *
 * // when p matches any element beginning with the third
 * // and then each member starts with 'a'
 * Path p = Path.of("[2, -1]/#a.*");
 *
 * // then
 * List<JsonValue> result = p.apply(given).toList();
 * assert result.size() == 3; // three JSON objects...
 * assert result.get(0) == JsonBoolean.TRUE; // the "aa" member of the third object
 * assert result.get(1).equals(Basic.of(2)); // the "ab" member of the fourth
 * assert result.get(2).equals(Basic.of(3)); // the "ac" member of the fifth
 *}
 * <p>
 * The second approach to constructing paths is through the fluent API, as in
 * {@snippet :
 * Path.root().member("a").index(1).regex("b.*c").range(0, 2);
 *}
 * <p>
 * The class implements {@link Function} such that it may be used in stream
 * pipelines easily as in
 * <p>
 * {@snippet :
 * import java.util.List;
 * import io.github.ralfspoeth.greyson.*;import io.github.ralfspoeth.json.data.JsonArray;import io.github.ralfspoeth.json.data.JsonValue;
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
            this.regex = regex;
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
     * The first element found by the path.
     */
    public Optional<JsonValue> first(JsonValue root) {
        return apply(root).findFirst();
    }

    /**
     * The only element found by the path if it results in
     * a singleton list; otherwise {@link Optional#empty()}.
     */
    public Optional<JsonValue> single(JsonValue root) {
        var l = apply(root).toList();
        return l.size() == 1 ? Optional.of(l.getFirst()) : Optional.empty();
    }

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

    public Path member(String memberName) {
        return new MemberPath(memberName, this);
    }

    public Path index(int index) {
        return new IndexPath(index, this);
    }

    public Path range(int min, int max) {
        return new RangePath(min, max, this);
    }

    public Path regex(String regex) {
        return new RegexPath(regex, this);
    }

    public Path regex(Pattern regex) {
        return new RegexPath(regex, this);
    }

    public Path resolve(Path p) {
        if (this instanceof AbstractPath tp && p instanceof AbstractPath ap) {
            return tp.resolve(ap);
        } if(p instanceof AbstractPath ap) {
            assert this instanceof RootPath;
            return ap;
        } else {
            return this;
        }
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
