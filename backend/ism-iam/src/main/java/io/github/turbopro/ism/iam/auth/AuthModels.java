package io.github.turbopro.ism.iam.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AuthModels {
    private AuthModels() {}

    public record LoginCommand(
            @NotBlank String tenantCode,
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank @Size(max = 128) String deviceId) {}

    public record RefreshCommand(
            @NotBlank String refreshToken,
            @NotBlank @Size(max = 128) String deviceId) {}

    public record LogoutCommand(@NotBlank String refreshToken) {}

    public record ChangePasswordCommand(
            @NotBlank String oldPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {}

    public record UserSummary(String id, String tenantId, String username, String displayName,
                              boolean passwordChangeRequired) {}

    public record TokenPair(String accessToken, String refreshToken, long expiresIn,
                            UserSummary user) {}

    public record AuthUser(long id, long tenantId, String username, String displayName,
                           String passwordHash, String status, boolean forcePasswordChange,
                           LocalDateTime lockedUntil, int failedCount, int tokenVersion) {}

    public record RefreshTokenRecord(long id, long tenantId, long userId, String tokenHash,
                                     String familyId, LocalDateTime expiresAt, LocalDateTime revokedAt,
                                     String replacedByHash, String deviceId) {}
}
