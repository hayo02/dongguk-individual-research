package kr.ac.dongguk.individualresearch.application;

public record ApplicationUpdateRequest(
        String contact,
        String applicationReason,
        String researchPurpose
) {
}
