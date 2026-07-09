package kr.ac.dongguk.individualresearch.application;

import java.util.List;

public record ApplicationValidationResponse(
        boolean valid,
        List<String> missingFields,
        List<String> missingFiles
) {}
