package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.data.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.ralfspoeth.json.query.Pointer.self;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

/**
 * A structural description of a {@link JsonValue}, used to <em>check</em> a
 * document ("what is wrong with it, and where?") and to <em>filter</em> a stream
 * of values ("keep the ones shaped like this").
 *
 * <p>A {@code Shape} maps a value to the {@link Violation}s it exhibits, so
 * an empty stream means the value satisfies the description:
 * {@snippet :
 * var shape = Shape.member("isin", Shape.string())
 *         .and(Shape.member("ccy", Shape.string()));
 *
 * shape.violations(doc).forEach(System.out::println);
 * // instrument/ccy: expected string, got number
 *}
 *
 * <p>The same description also yields a {@link #predicate()} for stream work.
 * Because the violation stream is lazy, the predicate stops at the first problem
 * and never builds a message it will not use:
 * {@snippet :
 * var bonds = doc.children().filter(shape.predicate()).toList();
 *}
 * Where {@link Selector} filters by <em>type</em> ({@link Selector#objects()},
 * {@link Selector#numbers()}, &hellip;), a {@code Shape} filters by
 * <em>shape</em> — and {@link Selector#where(Shape)} keeps that inside the
 * query algebra rather than dropping out to {@link Stream#filter}.
 *
 * <p>The three query types compose in both directions:
 * {@link Pointer#select(Selector)} and {@link Selector#point(Pointer)} bridge
 * pointer and selector, {@link Pointer#must(Shape)} asserts a shape at a
 * location, and {@link Selector#where(Shape)} narrows a selection by shape.
 *
 * <h2>Scope</h2>
 * This is deliberately <em>not</em> a JSON Schema implementation. When a
 * document has a schema that is authoritative for the operation at hand, use a
 * schema validator; {@code Shape} is for data in the wild, where no such
 * document exists or none is being honoured. Consequently there is no schema
 * I/O in either direction and no {@code $ref} resolution — both need a schema
 * <em>document</em> to mean anything. Violation messages are therefore local
 * and self-describing: they never point at a spec, because there is none.
 *
 * <p>That test asks whether a schema governs the operation, not which keywords
 * a schema language happens to have. {@link #and(Shape)} and {@link #or(Shape)}
 * overlap with {@code allOf} and {@code anyOf} and belong here regardless,
 * because conjunction and alternation describe messy data rather than specs.
 * There is no {@code not}: negation has nothing useful to report about what it
 * expected.
 *
 * <p>Shapes are obtained from the factory methods below and are immutable value
 * objects: equal shapes compare equal, hash consistently, and are usable as map
 * keys. The implementing types are intentionally not part of the public API, so
 * a description is read through {@link #explain()} rather than by matching on
 * its structure.
 */
public sealed interface Shape extends Function<JsonValue, Stream<Shape.Violation>>
        permits TypeShape, MemberShape, EachShape, SizeShape, AtShape, AndShape, OrShape {

    /**
     * A single structural defect: what is wrong, and where.
     *
     * @param at      the location of the offending value, relative to the value
     *                the {@code Shape} was applied to
     * @param message a self-describing, one-line explanation
     */
    record Violation(Pointer at, String message) {
        public Violation {
            requireNonNull(at);
            requireNonNull(message);
        }

        /**
         * A mismatch between what a shape wanted and what it found, phrased
         * {@code "expected <wanted>, got <found>"} — the one place that wording
         * lives.
         *
         * <p>{@code found} is a {@link String} rather than a {@link JsonValue}
         * because not every mismatch is about a type: a {@code size} constraint
         * reports the count it found, having been given the right type.
         *
         * @param at     where the defect is
         * @param wanted a noun phrase reading correctly after "expected"
         * @param found  what was there instead
         */
        static Violation expected(Pointer at, String wanted, String found) {
            return new Violation(at, "expected " + wanted + ", got " + found);
        }

        /** Re-root this violation underneath {@code base}. */
        Violation rebase(Pointer base) {
            return new Violation(base.resolve(at), message);
        }

        @Override
        public String toString() {
            var path = at.toString();
            return (path.isEmpty() ? "<root>" : path) + ": " + message;
        }
    }

    /**
     * The violations {@code value} exhibits, empty when it satisfies this
     * shape. The stream is lazy, so callers that only need to know
     * <em>whether</em> it is empty do not pay for the messages.
     */
    @Override
    Stream<Violation> apply(JsonValue value);

    /**
     * Alias for {@link #apply(JsonValue)}, reading better at call sites.
     */
    default Stream<Violation> violations(JsonValue value) {
        return apply(value);
    }

    /**
     * A short, one-line rendering of <em>this whole shape</em>, for showing a
     * description to a human:
     * {@snippet :
     * Shape.member("isin", Shape.string()).and(Shape.member("ccy", Shape.string())).explain();
     * // "isin": string and "ccy": string
     *}
     *
     * <p>This is deliberately <em>not</em> specified as a fragment that reads
     * correctly after the word "expected". For simple shapes it happens to —
     * {@code string}, {@code (array or object)} — and those shapes do pass it to
     * {@link Violation#expected(Pointer, String, String)}. For composites it does
     * not: <em>expected "ccy": string</em> and <em>expected a and b</em> both read
     * badly, which is why {@code and} reports its parts' violations individually
     * rather than describing itself. A shape building a mismatch therefore states
     * its wanted phrase at the call site instead of assuming {@code explain()}
     * will fit.
     */
    String explain();

    /**
     * {@code true} for values with no violations. Short-circuits at the first
     * problem.
     */
    default Predicate<JsonValue> predicate() {
        return value -> apply(value).findAny().isEmpty();
    }

    /**
     * Both this shape and {@code other}; violations of the two are
     * concatenated, lazily. Nested conjunctions are flattened.
     */
    default Shape and(Shape other) {
        requireNonNull(other);
        var parts = new ArrayList<Shape>();
        if (this instanceof AndShape(var mine)) parts.addAll(mine); else parts.add(this);
        if (other instanceof AndShape(var theirs)) parts.addAll(theirs); else parts.add(other);
        return new AndShape(parts);
    }

    /**
     * Either this shape or {@code other}; satisfied as soon as one of them is.
     * Nested alternations are flattened.
     *
     * <p>Use it for the union-typed fields real feeds produce — a member that is
     * an array in most documents and a bare object in some:
     * {@snippet :
     * var positions = Shape.member("positions", Shape.array().or(Shape.object()));
     *}
     *
     * <p>When <em>no</em> branch holds, the result is a <b>single</b> violation at
     * the location of the choice, naming the alternatives:
     * {@snippet :
     * // positions: expected array or object, got string
     *}
     * The branch violations are deliberately not reported. A value that only had
     * to satisfy one alternative would otherwise accumulate a defect per branch,
     * reading as several problems when there is one unsatisfied choice — and
     * picking a "closest" branch to blame would need a heuristic no message could
     * explain. The cost is that alternation reports more coarsely than
     * {@link #and(Shape)}: one message at the choice point rather than a pointer
     * into the offending sub-value.
     */
    default Shape or(Shape other) {
        requireNonNull(other);
        var parts = new ArrayList<Shape>();
        if (this instanceof OrShape(var mine)) parts.addAll(mine); else parts.add(this);
        if (other instanceof OrShape(var theirs)) parts.addAll(theirs); else parts.add(other);
        return new OrShape(parts);
    }

    // ---- factories --------------------------------------------------------

    /** Accepts any value; the identity of {@link #and(Shape)}. */
    static Shape anything() {
        return new AndShape(List.of());
    }

    /**
     * Accepts nothing; the identity of {@link #or(Shape)}, and so the seed for
     * folding a set of alternatives:
     * {@snippet :
     * var any = alternatives.stream().reduce(Shape.nothing(), Shape::or);
     *}
     */
    static Shape nothing() {
        return new OrShape(List.of());
    }

    static Shape type(Class<? extends JsonValue> expected) {
        return new TypeShape(expected);
    }

    static Shape string() {
        return new TypeShape(JsonString.class);
    }

    static Shape number() {
        return new TypeShape(JsonNumber.class);
    }

    static Shape bool() {
        return new TypeShape(JsonBoolean.class);
    }

    static Shape nul() {
        return new TypeShape(JsonNull.class);
    }

    static Shape object() {
        return new TypeShape(JsonObject.class);
    }

    static Shape array() {
        return new TypeShape(JsonArray.class);
    }

    /**
     * The object carries {@code key}, and its value satisfies {@code shape}.
     * Naming a member <em>is</em> requiring it: if a member is of no interest,
     * do not mention it; if only its presence matters, use {@link #member(String)}.
     */
    static Shape member(String key, Shape shape) {
        return new MemberShape(key, shape);
    }

    /** The object carries {@code key}, whatever is stored there. */
    static Shape member(String key) {
        return new MemberShape(key, anything());
    }

    /** An array whose every element satisfies {@code shape}. */
    static Shape each(Shape shape) {
        return new EachShape(shape);
    }

    /** An aggregate of exactly {@code size} elements or members. */
    static Shape size(int size) {
        return new SizeShape(size, size);
    }

    /** An aggregate holding between {@code min} and {@code max}, both inclusive. */
    static Shape size(int min, int max) {
        return new SizeShape(min, max);
    }

    /** An aggregate holding at least {@code min} elements or members. */
    static Shape atLeast(int min) {
        return new SizeShape(min, Integer.MAX_VALUE);
    }
}

// ---- implementations ------------------------------------------------------
// Package-private on purpose: these carry no API promise, so their shapes stay
// free to change. Records rather than classes so equality, hashing and
// toString come for free — Shape is documented as a value object, and that
// guarantee is cheaper to keep than to hand-write six times.

/** The value must be an instance of {@code expected}. */
record TypeShape(Class<? extends JsonValue> expected) implements Shape {
    TypeShape {
        requireNonNull(expected);
    }

    @Override
    public Stream<Violation> apply(JsonValue value) {
        return expected.isInstance(value)
                ? Stream.empty()
                : Stream.of(Violation.expected(self(), explain(), value.typeName()));
    }

    @Override
    public String explain() {
        if(expected.equals(JsonNull.class)) return "null";
        else if(expected.equals(JsonBoolean.class)) return "true|false";
        else if(expected.equals(JsonString.class)) return "string";
        else if(expected.equals(JsonNumber.class)) return "number";
        else if(expected.equals(Basic.class)) return "null|true|false|string|number";
        else if(expected.equals(JsonObject.class)) return "object";
        else if(expected.equals(JsonArray.class)) return "array";
        else if(expected.equals(Aggregate.class)) return "array|object";
        else throw new AssertionError();
    }
}

/**
 * The value must be an object carrying {@code key}, and what is stored there
 * must satisfy {@code shape}.
 */
record MemberShape(String key, Shape shape) implements Shape {
    MemberShape {
        requireNonNull(key);
        requireNonNull(shape);
    }

    @Override
    public Stream<Violation> apply(JsonValue value) {
        // "be an object" is a precondition this shape shares with object(),
        // so let that shape phrase the complaint rather than repeating it
        if (!(value instanceof JsonObject(var members))) {
            return Shape.object().apply(value);
        }
        var member = members.get(key);
        // the violation points at the key itself, not at the object
        return member == null
                ? Stream.of(new Violation(self().member(key), "missing required member"))
                : shape.apply(member).map(v -> v.rebase(self().member(key)));
    }

    @Override
    public String explain() {
        return "\"" + key + "\": " + shape.explain();
    }
}

/** The value must be an array whose every element satisfies {@code shape}. */
record EachShape(Shape shape) implements Shape {
    EachShape {
        requireNonNull(shape);
    }

    @Override
    public Stream<Violation> apply(JsonValue value) {
        if (!(value instanceof JsonArray(var elements))) {
            return Shape.array().apply(value);
        }
        return IntStream.range(0, elements.size())
                .boxed()
                .flatMap(i -> shape.apply(elements.get(i)).map(v -> v.rebase(self().index(i))));
    }

    @Override
    public String explain() {
        return "each element " + shape.explain();
    }
}

/**
 * The value must be an {@link Aggregate} holding between {@code min} and
 * {@code max} elements or members, both inclusive.
 */
record SizeShape(int min, int max) implements Shape {
    SizeShape {
        if (min < 0) throw new IllegalArgumentException("min must not be negative: " + min);
        if (max < min) throw new IllegalArgumentException("max " + max + " is below min " + min);
    }

    @Override
    public Stream<Violation> apply(JsonValue value) {
        if (!(value instanceof Aggregate aggregate)) {
            return Shape.array().or(Shape.object()).apply(value);
        }
        int size = aggregate.size();
        // the type was right, so what was "found" is the count, not a type name
        return (size >= min && size <= max)
                ? Stream.empty()
                : Stream.of(Violation.expected(self(), explain(), String.valueOf(size)));
    }

    @Override
    public String explain() {
        if (min == max) return min + " element(s)";
        return max == Integer.MAX_VALUE
                ? "at least " + min + " element(s)"
                : "between " + min + " and " + max + " element(s)";
    }
}

/**
 * The value must resolve at {@code at}, and what is found there must satisfy
 * {@code shape}. Built via {@link Pointer#must(Shape)}.
 */
record AtShape(Pointer at, Shape shape) implements Shape {
    AtShape {
        requireNonNull(at);
        requireNonNull(shape);
    }

    @Override
    public Stream<Violation> apply(JsonValue value) {
        return at.apply(value)
                .map(found -> shape.apply(found).map(v -> v.rebase(at)))
                .orElseGet(() -> Stream.of(new Violation(at, "no value at this location")));
    }

    @Override
    public String explain() {
        var path = at.toString();
        return (path.isEmpty() ? "<root>" : path) + " " + shape.explain();
    }
}

/** Every part must hold; an empty conjunction accepts anything. */
record AndShape(List<Shape> parts) implements Shape {
    AndShape {
        parts = List.copyOf(parts);
    }

    @Override
    public Stream<Violation> apply(JsonValue value) {
        // flatMap keeps this lazy, so predicate() still short-circuits
        return parts.stream().flatMap(part -> part.apply(value));
    }

    @Override
    public String explain() {
        return parts.isEmpty()
                ? "anything"
                : parts.stream().map(Shape::explain).collect(joining(" and "));
    }
}

/**
 * At least one part must hold. An empty alternation holds for nothing: there is
 * no branch to satisfy, which is the dual of an empty conjunction accepting
 * everything.
 */
record OrShape(List<Shape> parts) implements Shape {
    OrShape {
        parts = List.copyOf(parts);
    }

    @Override
    public Stream<Violation> apply(JsonValue value) {
        // findAny() on each branch stops at its first defect, and anyMatch stops
        // at the first branch that holds, so a satisfied alternation never builds
        // a message at all
        boolean satisfied = parts.stream().anyMatch(part -> part.apply(value).findAny().isEmpty());
        if (satisfied) return Stream.empty();
        // one violation at the choice point — see Shape#or(Shape) for why the
        // branch violations are not propagated
        return Stream.of(Violation.expected(self(), explain(), value.typeName()));
    }

    @Override
    public String explain() {
        // parenthesised so that mixing with "and" stays unambiguous:
        // (array or object) and "id": string
        return parts.isEmpty()
                ? "nothing"
                : parts.size() == 1
                ? parts.getFirst().explain()
                : parts.stream().map(Shape::explain).collect(joining(" or ", "(", ")"));
    }
}
