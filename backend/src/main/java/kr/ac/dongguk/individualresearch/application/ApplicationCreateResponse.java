package kr.ac.dongguk.individualresearch.application;

public record ApplicationCreateResponse(
        long applicationId,
        String status,
        long courseId
) {
}
