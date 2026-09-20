package io.github.turbopro.ism.iam.auth;

import io.github.turbopro.ism.common.api.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AuthMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final Duration refreshTtl;
    private final int maxFailures;
    private final Duration lockDuration;
    private final String dummyHash;

    public AuthService(AuthMapper mapper, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService,
                       @Value("${ism.security.jwt.refresh-ttl:P7D}") Duration refreshTtl,
                       @Value("${ism.security.login.max-failures:5}") int maxFailures,
                       @Value("${ism.security.login.lock-duration:PT15M}") Duration lockDuration) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTtl = refreshTtl;
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
        this.dummyHash = passwordEncoder.encode("dummy-password-never-used");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthModels.TokenPair login(AuthModels.LoginCommand command, String ip) {
        LocalDateTime now = LocalDateTime.now();
        AuthModels.AuthUser user = mapper.findForLogin(command.tenantCode(), command.username());
        if (user == null) {
            passwordEncoder.matches(command.password(), dummyHash);
            throw new ApiException(IamErrorCode.INVALID_CREDENTIALS);
        }
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(now)) {
            throw new ApiException(IamErrorCode.ACCOUNT_LOCKED);
        }
        if (!"ACTIVE".equals(user.status()) || !passwordEncoder.matches(command.password(), user.passwordHash())) {
            int failures = user.failedCount() + 1;
            LocalDateTime lockedUntil = failures >= maxFailures ? now.plus(lockDuration) : null;
            mapper.updateLoginFailure(user.id(), failures, lockedUntil);
            throw new ApiException(IamErrorCode.INVALID_CREDENTIALS);
        }
        mapper.markLoginSuccess(user.id(), now);
        return issuePair(user, UUID.randomUUID().toString(), command.deviceId(), ip, now);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthModels.TokenPair refresh(AuthModels.RefreshCommand command, String ip) {
        LocalDateTime now = LocalDateTime.now();
        String oldHash = hash(command.refreshToken());
        AuthModels.RefreshTokenRecord current = mapper.lockRefreshToken(oldHash);
        if (current == null) {
            throw new ApiException(IamErrorCode.TOKEN_INVALID);
        }
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
        AuthModels.AuthUser user = mapper.findById(current.userId());
        if (user == null || !"ACTIVE".equals(user.status())) {
            mapper.revokeFamily(current.familyId(), now, "USER_INACTIVE");
            throw new ApiException(IamErrorCode.TOKEN_INVALID);
        }
        String refreshToken = randomToken();
        String replacementHash = hash(refreshToken);
        mapper.insertRefreshToken(randomId(), user.tenantId(), user.id(), replacementHash, current.familyId(),
                now, now.plus(refreshTtl), command.deviceId(), ip);
        mapper.revokeToken(current.id(), now, "ROTATED", replacementHash);
        return tokenPair(user, refreshToken, current.familyId());
    }

    @Transactional
    public void logout(String refreshToken) {
        LocalDateTime now = LocalDateTime.now();
        AuthModels.RefreshTokenRecord current = mapper.lockRefreshToken(hash(refreshToken));
        if (current != null) {
            mapper.revokeFamily(current.familyId(), now, "LOGOUT");
        }
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void changePassword(long userId, AuthModels.ChangePasswordCommand command) {
        AuthModels.AuthUser user = mapper.findById(userId);
        if (user == null || !passwordEncoder.matches(command.oldPassword(), user.passwordHash())) {
            throw new ApiException(IamErrorCode.INVALID_CREDENTIALS);
        }
        if (!isStrong(command.newPassword()) || passwordEncoder.matches(command.newPassword(), user.passwordHash())) {
            throw new ApiException(IamErrorCode.PASSWORD_POLICY);
        }
        LocalDateTime now = LocalDateTime.now();
        mapper.changePassword(userId, passwordEncoder.encode(command.newPassword()), now);
        mapper.revokeUserTokens(userId, now, "PASSWORD_CHANGED");
    }

    public AuthModels.AuthUser requireActiveUser(long userId, int tokenVersion) {
        AuthModels.AuthUser user = mapper.findById(userId);
        if (user == null || !"ACTIVE".equals(user.status()) || user.tokenVersion() != tokenVersion) {
            throw new ApiException(IamErrorCode.TOKEN_INVALID);
        }
        return user;
    }

    public AuthModels.AuthUser requireActiveSession(long userId, int tokenVersion, String familyId) {
        AuthModels.AuthUser user = requireActiveUser(userId, tokenVersion);
        if (familyId == null || mapper.countActiveFamily(familyId, userId, LocalDateTime.now()) == 0) {
            throw new ApiException(IamErrorCode.TOKEN_INVALID);
        }
        return user;
    }

    private AuthModels.TokenPair issuePair(AuthModels.AuthUser user, String familyId, String deviceId,
                                            String ip, LocalDateTime now) {
        String refreshToken = randomToken();
        mapper.insertRefreshToken(randomId(), user.tenantId(), user.id(), hash(refreshToken), familyId,
                now, now.plus(refreshTtl), deviceId, ip);
        return tokenPair(user, refreshToken, familyId);
    }

    private AuthModels.TokenPair tokenPair(AuthModels.AuthUser user, String refreshToken, String familyId) {
        AuthModels.UserSummary summary = new AuthModels.UserSummary(Long.toString(user.id()),
                Long.toString(user.tenantId()), user.username(), user.displayName(), user.forcePasswordChange());
        return new AuthModels.TokenPair(jwtTokenService.createAccessToken(user, familyId), refreshToken,
                jwtTokenService.accessTtlSeconds(), summary);
    }

    private static boolean isStrong(String password) {
        return password.length() >= 12 && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit)
                && password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
    }

    private static String randomToken() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static long randomId() {
        return RANDOM.nextLong(Long.MAX_VALUE - 1) + 1;
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
