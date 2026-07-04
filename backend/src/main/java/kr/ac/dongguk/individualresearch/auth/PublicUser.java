package kr.ac.dongguk.individualresearch.auth;

public record PublicUser(
        long id,
        String loginId,
        String name,
        UserRole role,
        String department,
        String email,
        String phone
) {
}
