package io.github.turbopro.ism.iam.console;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.iam.auth.IamErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class ConsoleAuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ConsoleAuthMapper mapper;
    private final PasswordEncoder passwords;
    private final ConsoleJwtTokenService tokens;
    private final Duration refreshTtl;
    private final int maxFailures;
    private final Duration lockDuration;
    private final String dummyHash;

    public ConsoleAuthService(ConsoleAuthMapper mapper, PasswordEncoder passwords, ConsoleJwtTokenService tokens,
                              @Value("${ism.security.jwt.refresh-ttl:P7D}") Duration refreshTtl,
                              @Value("${ism.security.login.max-failures:5}") int maxFailures,
                              @Value("${ism.security.login.lock-duration:PT15M}") Duration lockDuration) {
        this.mapper = mapper;
        this.passwords = passwords;
        this.tokens = tokens;
        this.refreshTtl = refreshTtl;
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
        this.dummyHash = passwords.encode("dummy-console-password-never-used");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public ConsoleAuthModels.TokenPair login(ConsoleAuthModels.LoginCommand command, String ip) {
        LocalDateTime now = LocalDateTime.now();
        ConsoleAuthModels.PlatformUser user = mapper.findForLogin(command.username());
        if (user == null) {
            passwords.matches(command.password(), dummyHash);
            throw new ApiException(IamErrorCode.INVALID_CREDENTIALS);
        }
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(now)) {
            throw new ApiException(IamErrorCode.ACCOUNT_LOCKED);
        }
        if (!"ACTIVE".equals(user.status()) || !passwords.matches(command.password(), user.passwordHash())) {
            int failures = user.failedCount() + 1;
            mapper.updateLoginFailure(user.id(), failures,
                    failures >= maxFailures ? now.plus(lockDuration) : null);
            throw new ApiException(IamErrorCode.INVALID_CREDENTIALS);
        }
        mapper.markLoginSuccess(user.id(), now);
        return issuePair(user, UUID.randomUUID().toString(), command.deviceId(), ip, now);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public ConsoleAuthModels.TokenPair refresh(ConsoleAuthModels.RefreshCommand command, String ip) {
        LocalDateTime now = LocalDateTime.now();
        ConsoleAuthModels.RefreshToken current = mapper.lockRefreshToken(hash(command.refreshToken()));
        if (current == null) throw new ApiException(IamErrorCode.TOKEN_INVALID);
        if (current.revokedAt() != null) {
            if (current.replacedByHash() != null) {
                mapper.revokeFamily(current.familyId(), now, "TOKEN_REUSE");
                throw new ApiException(IamErrorCode.TOKEN_REUSED);
            }
            throw new ApiException(IamErrorCode.TOKEN_INVALID);
        }
        if (current.expiresAt().isBefore(now) || !current.deviceId().equals(command.deviceId())) {
            mapper.revokeFamily(current.familyId(), now, "EXPIRED_OR_DEVICE_MISMATCH");
            throw new ApiException(IamErrorCode.TOKEN_INVALID);
        }
        ConsoleAuthModels.PlatformUser user = requireActiveUser(current.userId(), -1);
        String refreshToken = randomToken();
        String replacementHash = hash(refreshToken);
        mapper.insertRefreshToken(randomId(), user.id(), replacementHash, current.familyId(), now,
                now.plus(refreshTtl), command.deviceId(), ip);
        mapper.revokeToken(current.id(), now, "ROTATED", replacementHash);
        return tokenPair(user, refreshToken, current.familyId());
    }

    @Transactional
    public void logout(String refreshToken) {
        ConsoleAuthModels.RefreshToken current = mapper.lockRefreshToken(hash(refreshToken));
        if (current != null) mapper.revokeFamily(current.familyId(), LocalDateTime.now(), "LOGOUT");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void changePassword(long userId, ConsoleAuthModels.ChangePasswordCommand command) {
        ConsoleAuthModels.PlatformUser user = mapper.findById(userId);
        if (user == null || !passwords.matches(command.oldPassword(), user.passwordHash())) {
            throw new ApiException(IamErrorCode.INVALID_CREDENTIALS);
        }
        if (!isStrong(command.newPassword()) || passwords.matches(command.newPassword(), user.passwordHash())) {
            throw new ApiException(IamErrorCode.PASSWORD_POLICY);
        }
        LocalDateTime now = LocalDateTime.now();
        mapper.changePassword(userId, passwords.encode(command.newPassword()), now);
        mapper.revokeUserTokens(userId, now, "PASSWORD_CHANGED");
    }

    ConsoleAuthModels.PlatformUser requireActiveSession(long userId, int version, String familyId) {
        ConsoleAuthModels.PlatformUser user = requireActiveUser(userId, version);
        if (familyId == null || mapper.countActiveFamily(familyId, userId, LocalDateTime.now()) == 0) {
            throw new ApiException(IamErrorCode.TOKEN_INVALID);
        }
        return user;
    }

    ConsoleAuthModels.PlatformUser requireActiveUser(long userId, int version) {
        ConsoleAuthModels.PlatformUser user = mapper.findById(userId);
        if (user == null || !"ACTIVE".equals(user.status()) || (version >= 0 && user.tokenVersion() != version)) {
            throw new ApiException(IamErrorCode.TOKEN_INVALID);
        }
        return user;
    }

    Set<String> permissions(long userId) { return mapper.findPermissions(userId); }

    ConsoleAuthModels.UserSummary summary(ConsoleAuthModels.PlatformUser user) {
        return new ConsoleAuthModels.UserSummary(Long.toString(user.id()), user.username(), user.displayName(),
                user.forcePasswordChange(), permissions(user.id()));
    }

    private ConsoleAuthModels.TokenPair issuePair(ConsoleAuthModels.PlatformUser user, String familyId,
                                                   String deviceId, String ip, LocalDateTime now) {
        String refresh = randomToken();
        mapper.insertRefreshToken(randomId(), user.id(), hash(refresh), familyId, now, now.plus(refreshTtl), deviceId, ip);
        return tokenPair(user, refresh, familyId);
    }

    private ConsoleAuthModels.TokenPair tokenPair(ConsoleAuthModels.PlatformUser user, String refresh, String familyId) {
        return new ConsoleAuthModels.TokenPair(tokens.createAccessToken(user, familyId), refresh,
                tokens.accessTtlSeconds(), summary(user));
    }

    private static String randomToken() {
        byte[] value = new byte[32]; RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
    private static long randomId() { return RANDOM.nextLong(Long.MAX_VALUE - 1) + 1; }
    private static boolean isStrong(String password) {
        return password.length() >= 12 && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit)
                && password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
    }
    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
