package kr.ac.dongguk.individualresearch.document;

import java.util.Set;

public record TemplateUploadResponse(
        TemplateRecord template, boolean valid, Set<String> foundPlaceholders, Set<String> missingPlaceholders
) {}
