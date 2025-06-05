package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.Element;
import io.github.ralfspoeth.json.JsonArray;
import io.github.ralfspoeth.json.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * <li>{@code name} where {@code name} is just the literal member name of JSON
 * object</li>
 * </ul>
 * Example:
 * <p>
 * {@snippet :
 * import io.github.ralfspoeth.json.Element;
 * import io.github.ralfspoeth.json.JsonBoolean;
 * import io.github.ralfspoeth.json.JsonNumber;
 * import io.github.ralfspoeth.json.io.JsonReader;
 *
 * import java.util.List;
 *
 * // given be a JSON array of five elements, two numbers and three JSON objects
 * var given = """
 *       [1, 2, {"aa": true}, {"xy": 5, "ab": 2}, {"ac": 3}]
 *       """;
 * var elem = JsonReader.readElement(given);
 *
 * // when p matches any element beginning with the third and then each member starts with 'a'
 * Path p = Path.of("[2, -1]/#a.*");
 *
 * // then
 * List<Element> result = p.apply(given).toList();
 * assert result.size() == 3; // three members of the last JSON objects start with 'a' assert
 * assert result.get(0) == JsonBoolean.TRUE;
 * assert result.get(1).equals(new JsonNumber(2));
 * assert result.get(2).equals(new JsonNumber(3));
 *}
 * <p>
 * The class implements {@link Function} such that it may be used in stream
 * pipelines easily as in
 * <p>
 * {@snippet :
 * import io.github.ralfspoeth.json.JsonArray;Path p = Path.of("..."); // @replace regex='"..."' replacement="..."
 * JsonArray a = new JsonArray(List.of()); // @replace regex='List.of()' replacement='...'
 * var result = a.stream().flatMap(p).toList(); // @highlight substring="flatMap(p)"
 *}
 */
public sealed abstract class Path implements Function<Element, Stream<Element>> {

    private static final class MemberPath extends Path {

        private final String memberName;

        private MemberPath(String memberName, Path parent) {
            super(parent);
            this.memberName = requireNonNull(memberName);
        }

        @Override
        Stream<Element> evalThis(Element elem) {
            return elem instanceof JsonObject(
                    Map<String, Element> members
            ) ? Stream.of(members.get(memberName)) : Stream.of();
        }

        @Override
        public int hashCode() {
            return memberName.hashCode();
        }

        @Override
        boolean equalsLast(Path p) {
            return p instanceof MemberPath mp && mp.memberName.equals(memberName);
        }
    }

    private static final class IndexPath extends Path {
        private final int index;

        private IndexPath(int index, Path parent) {
            super(parent);
            this.index = index;
        }

        @Override
        Stream<Element> evalThis(Element elem) {
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
        boolean equalsLast(Path p) {
            return p instanceof IndexPath ip && ip.index == index;
        }

        @Override
        public int hashCode() {
            return Objects.hash(index);
        }

    }

    private static final class RangePath extends Path {

        private final int min, max;

        private RangePath(int min, int max, Path parent) {
            super(parent);
            this.min = min;
            this.max = (max > 0 ? max : Integer.MAX_VALUE);
        }

        private Stream<Element> evalArray(List<Element> array) {
            return IntStream.range(min, max)
                    .takeWhile(i -> i < array.size())
                    .mapToObj(array::get);
        }

        @Override
        Stream<Element> evalThis(Element elem) {
            return elem instanceof JsonArray(var elements) ? evalArray(elements) : Stream.of();
        }

        @Override
        boolean equalsLast(Path p) {
            return p instanceof RangePath rp && rp.min == min && rp.max == max;
        }

        @Override
        public int hashCode() {
            return Objects.hash(max, min);
        }
    }

    private static final class RegexPath extends Path {

        private final Pattern regex;

        private RegexPath(String regex, Path parent) {
            this(Pattern.compile(regex), parent);
        }

        private RegexPath(Pattern regex, Path parent) {
            super(parent);
            this.regex = regex;
        }

        @Override
        Stream<Element> evalThis(Element elem) {
            return elem instanceof JsonObject o ? evalObject(o) : Stream.of();
        }

        Stream<Element> evalObject(JsonObject o) {
            return o.members()
                    .entrySet()
                    .stream()
                    .filter(e -> regex.matcher(e.getKey()).matches())
                    .map(Map.Entry::getValue);
        }

        @Override
        boolean equalsLast(Path p) {
            return p instanceof RegexPath rp && rp.regex.equals(regex);
        }

        @Override
        public int hashCode() {
            return regex.hashCode();
        }
    }

    private final Path parent;

    protected Path(Path parent) {
        this.parent = parent;
    }

    @Override
    public Stream<Element> apply(Element root) {
        return parent == null ? this.evalThis(root) : parent.apply(root).flatMap(this::evalThis);
    }

    Optional<Element> first(Element root) {
        return apply(root).findFirst();
    }

    Optional<Element> single(Element root) {
        var l = apply(root).toList();
        return l.size() == 1 ? Optional.of(l.get(0)) : Optional.empty();
    }

    abstract Stream<Element> evalThis(Element elem);

    private static final Pattern INDEX_PATTERN = Pattern.compile("\\[(-?\\d+)]");

    private static final Pattern RANGE_PATTERN = Pattern.compile("\\[(\\d+)\\.\\.(-?\\d+)]");

    public static Path of(String pattern) {
        var parts = requireNonNull(pattern).split("/");
        Path prev = null;
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

    public static double doubleValue(Path p, Element root) {
        return p.single(root).map(Queries::doubleValue).orElse(0d);
    }

    public static double doubleValue(String path, Element root) {
        return doubleValue(of(path), root);
    }

    public static boolean booleanValue(Path p, Element root) {
        return p.single(root).map(Queries::booleanValue).orElse(false);
    }

    public static boolean booleanValue(String path, Element root) {
        return booleanValue(of(path), root);
    }

    public static int intValue(Path p, Element root) {
        return p.single(root).map(Queries::intValue).orElse(0);
    }

    public static int intValue(String path, Element root) {
        return intValue(of(path), root);
    }

    public static long longValue(Path p, Element root) {
        return p.single(root).map(Queries::longValue).orElse(0L);
    }

    public static long longValue(String path, Element root) {
        return longValue(of(path), root);
    }

    public static <E extends Enum<E>> Enum<E> enumValue(Path p, Element root, Class<E> enumClass) {
        return p.single(root).map(e -> Queries.enumValue(enumClass, e)).orElse(null);
    }

    public static <E extends Enum<E>> Enum<E> enumValue(String path, Element root, Class<E> enumClass) {
        return enumValue(of(path), root, enumClass);
    }

    public static String stringValue(Path p, Element root) {
        return p.single(root).map(Queries::stringValue).orElse(null);
    }

    public static String stringValue(String path, Element root) {
        return stringValue(of(path), root);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Path p && equalsLast(p) && Objects.equals(p.parent, parent);
    }

    abstract boolean equalsLast(Path p);

    @Override
    public abstract int hashCode();
}
