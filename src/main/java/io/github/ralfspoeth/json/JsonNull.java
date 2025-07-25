package io.github.ralfspoeth.json;

public final class JsonNull implements Basic<Object> {
    public static final JsonNull INSTANCE = new JsonNull();

    private JsonNull(){}

    @Override
    public String json() {
        return "null";
    }

    @Override
    public Object value() {return null;}

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[value=null]";
    }

    @Override
    public boolean test(JsonValue o) {
        return o instanceof JsonNull;
    }
}
