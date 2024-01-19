package io.github.ralfspoeth.json;

public enum JsonBoolean implements Basic<Boolean> {
    TRUE{
        @Override
        public String json() {
            return "true";
        }

        @Override
        public Boolean value() {
            return Boolean.TRUE;
        }
    }, FALSE{
        @Override
        public String json() {
            return "false";
        }

        @Override
        public Boolean value() {
            return Boolean.FALSE;
        }
    };

    public static JsonBoolean of(boolean b) {
        return b?TRUE:FALSE;
    }
}
