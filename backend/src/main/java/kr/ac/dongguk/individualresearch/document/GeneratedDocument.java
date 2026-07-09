package kr.ac.dongguk.individualresearch.document;

import java.time.LocalDateTime;

public record GeneratedDocument(
        long id, long draftId, Long templateId, String documentType, String filename,
        String filePath, String fileHash, LocalDateTime createdAt, LocalDateTime expiresAt
) {}
