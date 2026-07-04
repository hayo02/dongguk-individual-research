package kr.ac.dongguk.individualresearch.auth;

public record LoginRequest(
        String loginId,
        String password
) {
}
