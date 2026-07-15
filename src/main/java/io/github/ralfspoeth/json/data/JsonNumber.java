package io.github.ralfspoeth.json.data;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * Representation of a JSON number by virtue of a {@link BigDecimal}.
 * The {@link #value} must not be {@code null} and is stored exactly as given, so
 * its scale — including any trailing zeros — is preserved and re-emitted by
 * {@link #json()} ({@code 18250.00} round-trips as {@code 18250.00}).
 * <p>
 * Equality is <em>numeric</em>: two {@code JsonNumber}s are equal when their
 * values {@linkplain BigDecimal#compareTo(BigDecimal) compare} equal, so
 * {@code 18250.00} equals {@code 18250}. {@link #hashCode()} is kept consistent
 * with that by stripping trailing zeros before hashing.
 * @param value the number, may not be {@code null}
 */
public record JsonNumber(BigDecimal value) implements Basic<BigDecimal> {
    public static final JsonNumber ZERO = new JsonNumber(BigDecimal.ZERO);

    public JsonNumber {
        requireNonNull(value);
    }

    ///  @return {@code value().toPlainString()} (never scientific notation)
    @Override
    public String json() {
        return value.toPlainString();
    }

    ///  @return {@code Optional.of(value())}
    @Override
    public Optional<BigDecimal> decimal() {
        return Optional.of(value);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof JsonNumber(var bd) && value.compareTo(bd)==0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }
}
