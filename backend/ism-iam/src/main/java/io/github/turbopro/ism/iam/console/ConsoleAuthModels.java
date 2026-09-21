package io.github.turbopro.ism.iam.console;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Set;

public final class ConsoleAuthModels {
    private ConsoleAuthModels() {}

    public record LoginCommand(@NotBlank String username, @NotBlank String password,
                               @NotBlank @Size(max = 128) String deviceId) {}
    public record RefreshCommand(@NotBlank String refreshToken,
                                 @NotBlank @Size(max = 128) String deviceId) {}
    public record LogoutCommand(@NotBlank String refreshToken) {}
    public record ChangePasswordCommand(@NotBlank String oldPassword,
                                        @NotBlank @Size(min = 12, max = 128) String newPassword) {}
    public record PlatformUser(long id, String username, String displayName, String passwordHash,
                               String status, boolean forcePasswordChange, LocalDateTime lockedUntil,
                               int failedCount, int tokenVersion) {}
    public record RefreshToken(long id, long userId, String tokenHash, String familyId,
                               LocalDateTime expiresAt, LocalDateTime revokedAt,
                               String replacedByHash, String deviceId) {}
    public record UserSummary(String id, String username, String displayName,
                              boolean passwordChangeRequired, Set<String> permissions) {}
    public record TokenPair(String accessToken, String refreshToken, long expiresIn, UserSummary user) {}
}
