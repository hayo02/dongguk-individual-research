package kr.ac.dongguk.individualresearch.application;

public class ApplicationForbiddenException extends RuntimeException {
    public ApplicationForbiddenException(String message) {
        super(message);
    }
}
