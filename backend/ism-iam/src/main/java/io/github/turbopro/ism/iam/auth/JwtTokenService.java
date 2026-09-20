package io.github.turbopro.ism.iam.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Duration accessTtl;

    public JwtTokenService(
            @Value("${ism.security.jwt.secret}") String secret,
            @Value("${ism.security.jwt.access-ttl:PT15M}") Duration accessTtl) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("ism.security.jwt.secret must contain at least 32 bytes");
        }
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains("ism-admin")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("industrial-supplier-management"), audienceValidator));
        this.decoder = jwtDecoder;
        this.accessTtl = accessTtl;
    }

    public String createAccessToken(AuthModels.AuthUser user, String tokenFamilyId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("industrial-supplier-management")
                .subject(Long.toString(user.id()))
                .audience(List.of("ism-admin"))
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .id(UUID.randomUUID().toString())
                .claim("tenantId", Long.toString(user.tenantId()))
                .claim("tokenVersion", user.tokenVersion())
                .claim("tokenFamilyId", tokenFamilyId)
                .claim("passwordChangeRequired", user.forcePasswordChange())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Jwt decode(String token) {
        return decoder.decode(token);
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
