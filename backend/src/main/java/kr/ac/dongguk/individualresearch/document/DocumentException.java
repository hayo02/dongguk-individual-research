package kr.ac.dongguk.individualresearch.document;

public class DocumentException extends RuntimeException {
    private final String errorCode;

    public DocumentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DocumentException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
