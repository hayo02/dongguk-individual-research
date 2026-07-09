package kr.ac.dongguk.individualresearch.common;

public record ApiResponse<T>(
        boolean success,
        String errorCode,
        String message,
        T data
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, null, data);
    }

    public static ApiResponse<Void> okMessage(String message) {
        return new ApiResponse<>(true, null, message, null);
    }

    public static ApiResponse<Void> fail(String message) {
        return new ApiResponse<>(false, "BAD_REQUEST", message, null);
    }

    public static ApiResponse<Void> fail(String errorCode, String message) {
        return new ApiResponse<>(false, errorCode, message, null);
    }
}
