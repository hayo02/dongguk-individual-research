package kr.ac.dongguk.individualresearch.auth;

public record LoginResponse(
        PublicUser user,
        String accessToken
) {
}
