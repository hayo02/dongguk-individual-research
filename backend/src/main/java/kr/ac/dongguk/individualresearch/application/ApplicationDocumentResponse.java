package kr.ac.dongguk.individualresearch.application;

public record ApplicationDocumentResponse(
        byte[] content,
        String filename,
        String contentType
) {
}
