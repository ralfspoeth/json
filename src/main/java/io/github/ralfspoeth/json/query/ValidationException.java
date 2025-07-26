package io.github.ralfspoeth.json.query;

public class ValidationException extends RuntimeException {

    private final Validation.Result result;

    public Validation.Result getResult() {
        return result;
    }

    public ValidationException(String message, Validation.Result result) {
        super(message);
        this.result = result;
    }
}
