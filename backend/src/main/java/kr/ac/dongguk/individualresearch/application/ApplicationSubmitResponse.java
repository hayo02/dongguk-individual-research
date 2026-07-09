package kr.ac.dongguk.individualresearch.application;

import java.time.LocalDateTime;

public record ApplicationSubmitResponse(long id, String status, LocalDateTime submittedAt) {}
