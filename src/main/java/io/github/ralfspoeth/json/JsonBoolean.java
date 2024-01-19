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

        @Override
        public boolean booleanValue() {
            return true;
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

        @Override
        public boolean booleanValue() {
            return false;
        }
    };

    public static JsonBoolean of(boolean b) {
        return b?TRUE:FALSE;
    }
}
