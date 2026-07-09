package kr.ac.dongguk.individualresearch.application.file;

public class ApplicationFileException extends RuntimeException {
    private final String errorCode;

    public ApplicationFileException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApplicationFileException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
