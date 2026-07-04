package kr.ac.dongguk.individualresearch.auth;

public record User(
        long id,
        String loginId,
        String passwordHash,
        String name,
        UserRole role,
        String department,
        String email,
        String phone
) {
    public PublicUser toPublicUser() {
        return new PublicUser(id, loginId, name, role, department, email, phone);
    }
}
