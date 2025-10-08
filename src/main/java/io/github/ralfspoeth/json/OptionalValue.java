package io.github.ralfspoeth.json;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;

import static java.util.Optional.ofNullable;

public class OptionalValue {
    private final @Nullable JsonValue value;

    public OptionalValue(@Nullable JsonValue value) {
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
        return value.longValue(def);
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

    public Optional<JsonValue> get(int index) {
        return value.get(index);
    }

    public OptionalValue get(String name) {
        return new OptionalValue(members().get(name));
    }

    public OptionalDouble doubleValue() {
        return value.doubleValue();
    }

    public double doubleValue(double def) {
        return value.doubleValue(def);
    }

    public List<JsonValue> elements() {
        return value.elements();
    }

    public Optional<Boolean> booleanValue() {
        return value.booleanValue();
    }

    public boolean booleanValue(boolean def) {
        return value.booleanValue(def);
    }

    public Optional<BigDecimal> decimalValue() {
        return value.decimalValue();
    }

    public BigDecimal decimalValue(BigDecimal def) {
        return value.decimalValue(def);
    }
}
