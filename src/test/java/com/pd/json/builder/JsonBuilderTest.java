package com.pd.json.builder;

import com.pd.json.data.JsonArray;
import com.pd.json.data.JsonString;
import org.junit.jupiter.api.Test;

import static com.pd.json.builder.JsonBuilder.arrayBuilder;
import static com.pd.json.builder.JsonBuilder.objectBuilder;
import static org.junit.jupiter.api.Assertions.*;

class JsonBuilderTest {

    @Test
    public void test1() {
        var obj = JsonBuilder.objectBuilder()
                .named("name", "Ralf")
                .named("income", 5)
                .named("sex", true)
                .named("adr", arrayBuilder()
                        .item(5)
                        .item(objectBuilder().named("sowat", "nix"))
                        .item(true)
                        .item(false)
                )
                .build();
        assertAll(
                () -> assertEquals(4, obj.members().size()),
                () -> assertEquals(4, ((JsonArray)obj.members().get("adr")).elements().size())
        );
    }

}