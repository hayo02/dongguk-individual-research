package kr.ac.dongguk.individualresearch.auth;

public record TokenPayload(
        long sub,
        UserRole role,
        long iat,
        long exp
) {
}
