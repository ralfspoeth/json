package io.github.ralfspoeth.json;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

public class OptionalValue {
    private final @Nullable JsonValue value;
    public static final OptionalValue NULL = new OptionalValue(null);

    OptionalValue(@Nullable JsonValue value) {
        this.value = value;
    }

    public String stringValue(String def) {
        return stringValue().orElse(def);
    }

    public Optional<String> stringValue() {
        return ofNullable(value).flatMap(JsonValue::stringValue);
    }

    public Map<String, JsonValue> members() {
        return ofNullable(value).map(JsonValue::members).orElse(Map.of());
    }

    public long longValue(long def) {
        return longValue().orElse(def);
    }

    public OptionalLong longValue() {
        return ofNullable(value).map(JsonValue::longValue).orElse(OptionalLong.empty());
    }

    public OptionalInt intValue() {
        return ofNullable(value).map(JsonValue::intValue).orElse(OptionalInt.empty());
    }

    public int intValue(int def) {
        return intValue().orElse(def);
    }

    public OptionalValue get(int index) {
        return switch (value) {
            case JsonArray(var lst) when index < lst.size() && index > 0 -> new OptionalValue(lst.get(index));
            case null, default -> NULL;
        };
    }

    public OptionalValue get(String name) {
        return new OptionalValue(members().get(name));
    }

    public OptionalDouble doubleValue() {
        return ofNullable(value).map(JsonValue::doubleValue).orElse(OptionalDouble.empty());
    }

    public double doubleValue(double def) {
        return doubleValue().orElse(def);
    }

    public List<JsonValue> elements() {
        return ofNullable(value).map(JsonValue::elements).orElse(List.of());
    }

    public Optional<Boolean> booleanValue() {
        return ofNullable(value).flatMap(JsonValue::booleanValue);
    }

    public boolean booleanValue(boolean def) {
        return value==null?def:value.booleanValue(def);
    }

    public Optional<BigDecimal> decimalValue() {
        return ofNullable(value).flatMap(JsonValue::decimalValue);
    }

    public BigDecimal decimalValue(BigDecimal def) {
        return decimalValue().orElse(def);
    }

    public <T> Optional<T> map(Function<? super JsonValue, T> f) {
        return ofNullable(value).map(f);
    }

    public JsonValue orElse(JsonValue other) {
        return ofNullable(value).orElse(other);
    }

    public JsonValue orElseThrow() {
        return ofNullable(value).orElseThrow();
    }
}
