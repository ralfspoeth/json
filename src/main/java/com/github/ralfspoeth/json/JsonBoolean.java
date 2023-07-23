package com.github.ralfspoeth.json;

public enum JsonBoolean implements Basic {
    TRUE{
        @Override
        public String json() {
            return "true";
        }
    }, FALSE{
        @Override
        public String json() {
            return "false";
        }
    };

    public static JsonBoolean of(boolean b) {
        return b?TRUE:FALSE;
    }
}
