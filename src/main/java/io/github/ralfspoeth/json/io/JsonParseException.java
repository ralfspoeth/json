package io.github.ralfspoeth.json.io;

public class JsonParseException extends RuntimeException {
    private final int row, column;

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public JsonParseException(String message, int row, int column) {
        super(message);
        this.row = row;
        this.column = column;
    }
}
