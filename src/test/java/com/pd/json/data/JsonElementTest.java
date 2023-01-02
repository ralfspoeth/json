package com.pd.json.data;

import com.pd.json.io.Writer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class JsonElementTest {

    @Test
    public void testElem(){
        JsonElement element = new JsonObject(Map.of(
                "name", new JsonString("Hallo"),
                "value", new JsonArray(List.of(new JsonNull(), new JsonTrue(), new JsonFalse(), new JsonNumber(5)))
        ));
        System.out.println(new Writer().toJson(element));
    }

}
