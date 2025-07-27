package io.github.ralfspoeth.json;

public record JsonBoolean(boolean boolValue) implements Basic<Boolean> {
    public static final JsonBoolean TRUE = new JsonBoolean(true);
    public static final JsonBoolean FALSE = new JsonBoolean(false);

    public static JsonBoolean of(boolean b) {
        return b ? TRUE : FALSE;
    }

    @Override
    public Boolean value() {
        return boolValue;
    }

    @Override
    public boolean test(JsonValue aBoolean) {
        return aBoolean instanceof JsonBoolean(boolean value) && value == boolValue;
    }

    @Override
    public String json() {
        return Boolean.toString(boolValue);
    }
}
