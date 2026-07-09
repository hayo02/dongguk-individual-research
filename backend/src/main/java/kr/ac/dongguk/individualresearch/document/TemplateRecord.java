package kr.ac.dongguk.individualresearch.document;

import java.time.LocalDateTime;

public record TemplateRecord(
        long id, Long noticeId, String semester, String templateName, String documentType,
        String originalFilename, String storedFilename, String filePath, int templateVersion,
        String fileHash, boolean active, boolean needsReview, LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
