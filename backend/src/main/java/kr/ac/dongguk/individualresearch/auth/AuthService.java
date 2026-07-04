package kr.ac.dongguk.individualresearch.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int TOKEN_TTL_SECONDS = 60 * 60 * 12;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ObjectMapper objectMapper;
    private final String secret;

    public AuthService(ObjectMapper objectMapper, @Value("${app.auth.secret}") String secret) {
        this.objectMapper = objectMapper;
        this.secret = secret;
    }

    public String hashPassword(String password) {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        byte[] digest = pbkdf2(password, salt, PBKDF2_ITERATIONS);
        return "pbkdf2_sha256$" + PBKDF2_ITERATIONS + "$"
                + Base64.getUrlEncoder().encodeToString(salt) + "$"
                + Base64.getUrlEncoder().encodeToString(digest);
    }

    public boolean verifyPassword(String password, String passwordHash) {
        try {
            String[] parts = passwordHash.split("\\$", 4);
            if (parts.length != 4 || !"pbkdf2_sha256".equals(parts[0])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = Base64.getUrlDecoder().decode(parts[3]);
            return MessageDigest.isEqual(pbkdf2(password, salt, iterations), expected);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public String issueAccessToken(PublicUser user) {
        long issuedAt = Instant.now().getEpochSecond();
        String payloadJson = "{\"exp\":" + (issuedAt + TOKEN_TTL_SECONDS)
                + ",\"iat\":" + issuedAt
                + ",\"role\":\"" + user.role().name()
                + "\",\"sub\":\"" + user.id() + "\"}";
        byte[] payloadBytes = payloadJson.getBytes(StandardCharsets.UTF_8);
        byte[] signature = hmac(payloadBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes)
                + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    public TokenPayload verifyAccessToken(String token) {
        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) {
            throw new AuthException("인증 정보가 올바르지 않습니다.");
        }

        byte[] payloadBytes;
        byte[] signature;
        try {
            payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            signature = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new AuthException("인증 정보가 올바르지 않습니다.");
        }

        if (!MessageDigest.isEqual(signature, hmac(payloadBytes))) {
            throw new AuthException("인증 정보가 올바르지 않습니다.");
        }

        try {
            JsonNode payload = objectMapper.readTree(payloadBytes);
            long expiresAt = payload.path("exp").asLong(0);
            if (expiresAt < Instant.now().getEpochSecond()) {
                throw new AuthException("인증 정보가 만료되었습니다.");
            }
            return new TokenPayload(
                    Long.parseLong(payload.path("sub").asText()),
                    UserRole.valueOf(payload.path("role").asText()),
                    payload.path("iat").asLong(),
                    expiresAt
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new AuthException("인증 정보가 올바르지 않습니다.");
        }
    }

    public String tokenHash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is not available", exception);
        }
    }

    private byte[] hmac(byte[] payloadBytes) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payloadBytes);
        } catch (Exception exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }
}
