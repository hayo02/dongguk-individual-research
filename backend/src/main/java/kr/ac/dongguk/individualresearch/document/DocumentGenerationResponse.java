package kr.ac.dongguk.individualresearch.document;

public record DocumentGenerationResponse(long documentId, String documentType, String filename, String downloadUrl) {}
