package kr.ac.dongguk.individualresearch.application;

public class ApplicationConflictException extends RuntimeException {
    public ApplicationConflictException(String message) {
        super(message);
    }
}
