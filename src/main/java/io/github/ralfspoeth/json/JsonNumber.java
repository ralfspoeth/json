package io.github.ralfspoeth.json;

import java.math.BigDecimal;

public sealed interface JsonNumber<T extends Number> extends Basic<T> permits JsonDouble, JsonLong, JsonBigDecimal {
    BigDecimal decimal();
}
