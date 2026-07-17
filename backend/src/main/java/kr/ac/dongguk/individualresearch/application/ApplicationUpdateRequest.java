package kr.ac.dongguk.individualresearch.application;

public record ApplicationUpdateRequest(
        String contact,
        String email,
        String applicationReason,
        String researchPurpose
) {
}
