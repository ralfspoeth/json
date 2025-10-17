package io.github.ralfspoeth.json.io;

/**
 * Thrown by the {@link JsonReader}; contains a message, a row, and a column,
 * both starting by 1
 */
public class JsonParseException extends RuntimeException {
    private final int row, column;

    public JsonParseException(String message, int row, int column) {
        super(message);
        this.row = row;
        this.column = column;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    @Override
    public String getMessage() {
        return "%s at row %d, column %d".formatted(super.getMessage(), row, column);
    }
}
