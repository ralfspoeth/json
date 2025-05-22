package io.github.ralfspoeth.json.io;

public class JsonParseException extends RuntimeException {
    private final int row, column;

    public JsonParseException(String message, int row, int column) {
        super(message);
        this.row = row;
        this.column = column;
    }

    @Override
    public String getMessage() {
        return "%s at row %d, column %d".formatted(super.getMessage(), row, column);
    }
}
