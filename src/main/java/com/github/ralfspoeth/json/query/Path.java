package com.github.ralfspoeth.json.query;

import com.github.ralfspoeth.json.JsonArray;
import com.github.ralfspoeth.json.JsonElement;
import com.github.ralfspoeth.json.JsonObject;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public sealed abstract class Path {

    private static final class MemberPath extends Path {

        private final String memberName;
        private MemberPath(String memberName, Path parent) {
            super(parent);
            this.memberName = requireNonNull(memberName);
        }
        @Override
        Stream<JsonElement> evalThis(JsonElement elem) {
            return elem instanceof JsonObject o? Stream.of(o.members().get(memberName)): Stream.of();
        }

        @Override
        public int hashCode() {
            return memberName.hashCode();
        }

        @Override
        boolean equalsSpecial(Path p) {
            return p instanceof MemberPath mp && mp.memberName.equals(memberName);
        }
    }

    private static final class RangePath extends Path {
        private final int min, max;

        private RangePath(int min, int max, Path parent) {
            super(parent);
            this.min = min;
            this.max = max;
        }

        Stream<JsonElement> evalArray(JsonArray array) {
            return IntStream.range(min, max).mapToObj(i -> array.elements().get(i));
        }

        @Override
        Stream<JsonElement> evalThis(JsonElement elem) {
            return elem instanceof JsonArray ja ? evalArray(ja) : Stream.of();
        }

        @Override
        boolean equalsSpecial(Path p) {
            return p instanceof RangePath rp && rp.min == min && rp.max == max;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    private static final class RegexPath extends Path {
        private final Pattern regex;
        private RegexPath(String regex, Path parent) {
            super(parent);
            this.regex = Pattern.compile(regex);
        }

        @Override
        Stream<JsonElement> evalThis(JsonElement elem) {
            return elem instanceof JsonObject o ? evalObject(o): Stream.of();
        }

        Stream<JsonElement> evalObject(JsonObject o) {
            return o.members()
                    .entrySet()
                    .stream()
                    .filter(e -> regex.matcher(e.getKey()).matches())
                    .map(Map.Entry::getValue);
        }

        @Override
        boolean equalsSpecial(Path p) {
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

    public Stream<JsonElement> evaluate(JsonElement root) {
        return parent==null ? this.evalThis(root) : parent.evaluate(root).flatMap(this::evalThis);
    }

    abstract Stream<JsonElement> evalThis(JsonElement elem);

    private static final Pattern RANGE_PATTERN = Pattern.compile("\\[(\\d+)\\.\\.(\\d+)\\]");
    public static Path of(String pattern) {
        var parts = requireNonNull(pattern).split("/");
        Path prev = null;
        for (String part : parts) {
            var m = RANGE_PATTERN.matcher(part);
            if(m.matches()) {
                prev = new RangePath(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), prev);
            } else if(part.startsWith("#")) {
                prev = new RegexPath(part.substring(1), prev);
            } else {
                prev = new MemberPath(part, prev);
            }
        }
        return prev;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Path p && equalsSpecial(p) && Objects.equals(p.parent, parent);
    }

    abstract boolean equalsSpecial(Path p);

    @Override
    public abstract int hashCode();
}
