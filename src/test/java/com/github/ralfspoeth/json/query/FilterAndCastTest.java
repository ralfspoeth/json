package com.github.ralfspoeth.json.query;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

class FilterAndCastTest {

    @Test
    void testFilterAndCast() {
        var l = Stream.of(1, 2d, true, false)
                .flatMap(FilterAndCast.filterAndCast(Boolean.class))
                .toList();
        System.out.println(l);
    }
}
