package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.data.JsonArray;
import io.github.ralfspoeth.json.data.JsonValue;

import java.util.function.Function;
import java.util.stream.Stream;

public sealed interface AltSel extends Function<JsonValue, Stream<JsonValue>> {

    record Range(int start, int end) implements AltSel {
        public Range {
            end = end < 0 ? Integer.MAX_VALUE:end;
        }

        @Override
        public Stream<JsonValue> apply(JsonValue v) {
            return switch (v) {
                case JsonArray(var ar) -> ar.subList(start, Math.max(ar.size(), end)).stream();
                default -> Stream.empty();
            };
        }
    }
}
