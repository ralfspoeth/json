package com.pd.json.conv;

import com.pd.json.data.*;
import com.pd.json.data.JsonNumber;
import com.pd.json.data.JsonObject;
import com.pd.json.data.JsonString;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.stream.Collectors;

public class Record2JsonSerializer {

    public JsonElement convert(Record r) {
        return new JsonObject(
                Arrays.stream(r.getClass().getRecordComponents())
                        .collect(Collectors.toMap(
                                RecordComponent::getName,
                                rc -> map(rc.getAccessor(), r))
                        )
        );
    }

    private JsonElement map(Method m, Record r) {
        try {
            var result = m.invoke(r);
            return switch (result) {
                case null -> JsonNull.INSTANCE;
                case Boolean b -> b ? JsonTrue.INSTANCE : JsonFalse.INSTANCE;
                case java.lang.Number n -> new JsonNumber(n.doubleValue());
                case CharSequence cs -> new JsonString(cs);
                case Record rec -> convert(rec);
                default -> {
                    if (result.getClass().isArray()) {
                        yield new JsonArray(Arrays.stream((JsonObject[]) result).collect(Collectors.toList()));
                    } else {
                        throw new RuntimeException();
                    }
                }
            };
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

}
