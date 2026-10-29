package io.github.turbopro.ism.iam.site;
import io.github.turbopro.ism.common.api.error.ApiException;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
class SiteContextTokensTest {
    static final String SECRET="site-context-unit-secret-at-least-32-bytes";
    static final Instant NOW=Instant.parse("2026-10-29T13:00:00Z");
    @Test void bindsIdentityAndExactCredentialAndUsesIndependentSignature(){
        var tokens=new SiteContextTokens(SECRET,Duration.ofMinutes(5),Clock.fixed(NOW,ZoneOffset.UTC));
        var issued=tokens.issue(10,20,30,"parent-access",NOW.plusSeconds(900));
        assertThat(tokens.verify(issued.value(),"parent-access",10,20).siteId()).isEqualTo(30);
        assertThatThrownBy(()->tokens.verify(issued.value(),"other-login",10,20)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->tokens.verify(issued.value(),"parent-access",11,20)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->tokens.verify(issued.value(),"parent-access",10,21)).isInstanceOf(ApiException.class);
        var other=new SiteContextTokens(SECRET+"different",Duration.ofMinutes(5),Clock.fixed(NOW,ZoneOffset.UTC));
        assertThatThrownBy(()->other.verify(issued.value(),"parent-access",10,20)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->tokens.verify("x".repeat(4097),"parent-access",10,20)).isInstanceOf(ApiException.class);
    }
    @Test void expiresStrictlyAtParentBoundAndNeverOutlivesIt(){
        var original=new SiteContextTokens(SECRET,Duration.ofMinutes(5),Clock.fixed(NOW,ZoneOffset.UTC));
        var issued=original.issue(10,20,30,"parent",NOW.plusSeconds(30));
        assertThat(issued.expiresAt()).isEqualTo(NOW.plusSeconds(30));
        var boundary=new SiteContextTokens(SECRET,Duration.ofMinutes(5),Clock.fixed(NOW.plusSeconds(30),ZoneOffset.UTC));
        assertThatThrownBy(()->boundary.verify(issued.value(),"parent",10,20)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->original.issue(10,20,30,"parent",NOW.plusMillis(500))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->original.issue(10,20,30,"parent",NOW)).isInstanceOf(ApiException.class);
    }
    @Test void rejectsWrongIssuerOrAudienceEvenWithValidPurposeSignature()throws Exception{
        var tokens=new SiteContextTokens(SECRET,Duration.ofMinutes(5),Clock.fixed(NOW,ZoneOffset.UTC));
        var issued=tokens.issue(10,20,30,"parent",NOW.plusSeconds(900));
        var claims=com.nimbusds.jwt.SignedJWT.parse(issued.value()).getJWTClaimsSet();
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
        byte[] key=mac.doFinal("ism-site-context-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var control=new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),claims);
        control.sign(new com.nimbusds.jose.crypto.MACSigner(key));assertThat(tokens.verify(control.serialize(),"parent",10,20).siteId()).isEqualTo(30);
        for(var invalid:java.util.List.of(new com.nimbusds.jwt.JWTClaimsSet.Builder(claims).issuer("foreign-issuer").build(),new com.nimbusds.jwt.JWTClaimsSet.Builder(claims).audience("ism-admin").build())){
            var signed=new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),invalid);signed.sign(new com.nimbusds.jose.crypto.MACSigner(key));
            assertThatThrownBy(()->tokens.verify(signed.serialize(),"parent",10,20)).isInstanceOf(ApiException.class);
        }
    }
    @Test void rejectsInvalidLifetimeConfiguration(){
        assertThatThrownBy(()->new SiteContextTokens(SECRET,Duration.ofMillis(500),Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new SiteContextTokens(SECRET,Duration.ZERO,Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new SiteContextTokens(SECRET,Duration.ofMinutes(16),Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
    }
}
