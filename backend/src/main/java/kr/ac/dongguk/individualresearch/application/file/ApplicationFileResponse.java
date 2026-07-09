package kr.ac.dongguk.individualresearch.application.file;

import java.time.LocalDateTime;

public record ApplicationFileResponse(
        long id,
        ApplicationFileDocumentType documentType,
        String fileName,
        long fileSize,
        String contentType,
        LocalDateTime uploadedAt
) {
    static ApplicationFileResponse from(ApplicationFileRecord record) {
        return new ApplicationFileResponse(
                record.id(), record.documentType(), record.originalFileName(),
                record.fileSize(), record.contentType(), record.uploadedAt());
    }
}
