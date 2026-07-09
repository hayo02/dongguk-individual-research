package kr.ac.dongguk.individualresearch.application;

public class ApplicationFlowException extends RuntimeException {
    private final String errorCode;

    public ApplicationFlowException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
