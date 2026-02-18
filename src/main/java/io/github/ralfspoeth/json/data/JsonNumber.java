package io.github.ralfspoeth.json.data;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * Representation of a JSON number
 * by virtue of a {@link BigDecimal}.
 * The {@link #value} must not be {@code null}.
 * Trailing zeros are stripped such that the equality
 * test for two numerically identical numbers is always {@code true}.
 * @param value the number, may not be {@code null}
 */
public record JsonNumber(BigDecimal value) implements Basic<BigDecimal> {
    public static final JsonNumber ZERO = new JsonNumber(BigDecimal.ZERO);

    public JsonNumber {
        value = requireNonNull(value).stripTrailingZeros();
    }

    ///  @return {@code value().toString()}
    @Override
    public String json() {
        return value.toString();
    }


    /**
     * {@code true} if {@code other} is another {@link JsonNumber}
     * and their respective values are numerically identical.
     */
    @Override
    public boolean test(@Nullable JsonValue other) {
        return other instanceof JsonNumber(BigDecimal bd) && bd.equals(value);
    }

    ///  @return {@code Optional.of(value())}
    @Override
    public Optional<BigDecimal> decimal() {
        return Optional.of(value);
    }
}
