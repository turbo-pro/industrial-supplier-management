package io.github.turbopro.ism.iam.console;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Component
public class ConsoleJwtTokenService {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Duration accessTtl;

    public ConsoleJwtTokenService(@Value("${ism.security.jwt.secret}") String secret,
                                  @Value("${ism.security.jwt.access-ttl:PT15M}") Duration accessTtl) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains("ism-console")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("industrial-supplier-management"), audience));
        this.decoder = jwtDecoder;
        this.accessTtl = accessTtl;
    }

    String createAccessToken(ConsoleAuthModels.PlatformUser user, String familyId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("industrial-supplier-management").subject(Long.toString(user.id()))
                .audience(List.of("ism-console")).issuedAt(now).expiresAt(now.plus(accessTtl))
                .id(UUID.randomUUID().toString()).claim("tokenVersion", user.tokenVersion())
                .claim("tokenFamilyId", familyId).claim("passwordChangeRequired", user.forcePasswordChange())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    Jwt decode(String token) { return decoder.decode(token); }
    long accessTtlSeconds() { return accessTtl.toSeconds(); }
}
