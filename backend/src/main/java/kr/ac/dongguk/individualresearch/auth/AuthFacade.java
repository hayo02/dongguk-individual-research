package kr.ac.dongguk.individualresearch.auth;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthFacade {
    private final AuthService authService;
    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    public AuthFacade(AuthService authService, UserRepository userRepository, RevokedTokenRepository revokedTokenRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    public LoginResponse login(LoginRequest request) {
        String loginId = request.loginId() == null ? "" : request.loginId().trim();
        String password = request.password() == null ? "" : request.password();
        if (!StringUtils.hasText(loginId) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("아이디와 비밀번호를 입력해 주세요.");
        }

        User user = userRepository.findByLoginId(loginId)
                .filter(found -> authService.verifyPassword(password, found.passwordHash()))
                .orElseThrow(() -> new AuthException("아이디 또는 비밀번호가 올바르지 않습니다."));
        PublicUser publicUser = user.toPublicUser();
        return new LoginResponse(publicUser, authService.issueAccessToken(publicUser));
    }

    public PublicUser currentUser(String authorizationHeader) {
        TokenContext context = verifyAuthorization(authorizationHeader);
        return userRepository.findById(context.payload().sub())
                .map(User::toPublicUser)
                .orElseThrow(() -> new AuthException("사용자 정보를 찾을 수 없습니다."));
    }

    public PublicUser currentUser(String authorizationHeader, UserRole requiredRole) {
        PublicUser user = currentUser(authorizationHeader);
        if (user.role() != requiredRole) {
            throw new AuthException("접근 권한이 없습니다.");
        }
        return user;
    }

    public void logout(String authorizationHeader) {
        TokenContext context = verifyAuthorization(authorizationHeader);
        revokedTokenRepository.revoke(
                authService.tokenHash(context.token()),
                context.payload().sub(),
                context.payload().exp()
        );
    }

    private TokenContext verifyAuthorization(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new AuthException("로그인이 필요합니다.");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        TokenPayload payload = authService.verifyAccessToken(token);
        if (revokedTokenRepository.existsByTokenHash(authService.tokenHash(token))) {
            throw new AuthException("로그인이 필요합니다.");
        }
        return new TokenContext(token, payload);
    }

    private record TokenContext(String token, TokenPayload payload) {
    }
}
