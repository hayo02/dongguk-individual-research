package kr.ac.dongguk.individualresearch.staff;

public record RevisionRequest(
        String reason,
        boolean requireSignedApplication
) {
}
