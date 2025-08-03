package io.github.ralfspoeth.json;

public record JsonNull() implements Basic<Object> {
    public static final JsonNull INSTANCE = new JsonNull();

    @Override
    public String json() {
        return "null";
    }

    @Override
    public Object value() {return null;}

    @Override
    public boolean test(JsonValue o) {
        return o instanceof JsonNull;
    }
}
