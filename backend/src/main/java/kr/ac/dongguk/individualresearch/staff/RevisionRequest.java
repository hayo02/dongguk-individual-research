package kr.ac.dongguk.individualresearch.staff;

import java.util.List;

public record RevisionRequest(
        String reason,
        boolean requireSignedApplication,
        List<String> revisionItems
) {
}
