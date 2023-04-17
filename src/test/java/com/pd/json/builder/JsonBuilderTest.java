package com.pd.json.builder;

import com.pd.json.data.JsonString;
import org.junit.jupiter.api.Test;

import static com.pd.json.builder.JsonBuilder.arrayBuilder;
import static com.pd.json.builder.JsonBuilder.objectBuilder;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonBuilderTest {

    @Test
    void test1() {
        var obj = objectBuilder()
                .named("name", "Ralf")
                .named("income", 5)
                .named("sex", true)
                .named("seven", new JsonString("murks"))
                .named("adr", arrayBuilder()
                        .item(5)
                        .item(objectBuilder().named("sowat", "nix"))
                        .item(true)
                        .item(false)
                )
                .build();
        assertAll(
                () -> assertEquals(5, obj.members().size()),
                () -> assertEquals(4, obj.getArray("adr").elements().size())
        );
    }

}