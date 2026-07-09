package kr.ac.dongguk.individualresearch.application.file;

import java.time.LocalDateTime;

public record ApplicationFileRecord(
        long id,
        long applicationId,
        ApplicationFileDocumentType documentType,
        String originalFileName,
        String storedFileName,
        String filePath,
        String contentType,
        long fileSize,
        String fileHash,
        long uploadedBy,
        LocalDateTime uploadedAt,
        LocalDateTime updatedAt
) {}
