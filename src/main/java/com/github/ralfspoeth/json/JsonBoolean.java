package com.github.ralfspoeth.json;

public enum JsonBoolean implements JsonValue {
    TRUE{
        @Override
        public String value() {
            return "true";
        }
    }, FALSE{
        @Override
        public String value() {
            return "false";
        }
    };
    public abstract String value();

    public static JsonBoolean of(boolean b) {
        return b?TRUE:FALSE;
    }
}
