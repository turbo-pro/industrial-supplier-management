package io.github.turbopro.ism.iam.site;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.turbopro.ism.common.api.error.ApiException;
import org.springframework.beans.factory.annotation.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
@Component
public class SiteContextTokens {
    private static final String ISSUER="industrial-supplier-management/site-context",AUDIENCE="ism-site-context";
    private final JwtEncoder encoder;private final JwtDecoder decoder;private final Duration ttl;private final Clock clock;
    public record Issued(String value,Instant expiresAt){}
    public record Verified(long siteId,Instant expiresAt){}
    @Autowired
    public SiteContextTokens(@Value("${ism.security.jwt.secret}")String secret,@Value("${ism.security.site-context-ttl:PT5M}")Duration ttl){this(secret,ttl,Clock.systemUTC());}
    SiteContextTokens(String secret,Duration ttl,Clock clock){
        if(secret.getBytes(StandardCharsets.UTF_8).length<32||ttl.compareTo(Duration.ofSeconds(1))<0||ttl.compareTo(Duration.ofMinutes(15))>0)throw new IllegalStateException("Invalid site context signing configuration");
        this.ttl=ttl;this.clock=clock;
        try{
            var purpose=Mac.getInstance("HmacSHA256");purpose.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            var key=new SecretKeySpec(purpose.doFinal("ism-site-context-v1".getBytes(StandardCharsets.UTF_8)),"HmacSHA256");
            encoder=new NimbusJwtEncoder(new ImmutableSecret<>(key));
            var jwtDecoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
            var time=new JwtTimestampValidator(Duration.ZERO);time.setClock(clock);
            OAuth2TokenValidator<Jwt> audience=jwt->List.of(AUDIENCE).equals(jwt.getAudience())?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(time,new JwtIssuerValidator(ISSUER),audience));decoder=jwtDecoder;
        }catch(java.security.GeneralSecurityException error){throw new IllegalStateException("Site context signing unavailable",error);}
    }
    public Issued issue(long tenantId,long userId,long siteId,String bearer,Instant accessExpires){
        if(tenantId<=0||userId<=0||siteId<=0||bearer==null||bearer.isBlank())throw invalid();
        Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        if(accessExpires==null||!accessExpires.isAfter(now))throw invalid();
        Instant expires=now.plus(ttl);if(accessExpires.isBefore(expires))expires=accessExpires;
        expires=expires.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        if(!expires.isAfter(clock.instant()))throw invalid();
        var claims=JwtClaimsSet.builder().issuer(ISSUER).audience(List.of(AUDIENCE)).subject(Long.toString(userId)).id(UUID.randomUUID().toString()).issuedAt(now).expiresAt(expires)
            .claim("tenantId",Long.toString(tenantId)).claim("siteId",Long.toString(siteId)).claim("ath",fingerprint(bearer)).build();
        return new Issued(encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue(),expires);
    }
    public Verified verify(String value,String bearer,long tenantId,long userId){
        try{
            if(bearer==null||bearer.isBlank())throw invalid();
            if(value==null||value.isBlank()||value.length()>4096)throw invalid();
            var jwt=decoder.decode(value);
            if(!Long.toString(tenantId).equals(jwt.getClaimAsString("tenantId"))||!Long.toString(userId).equals(jwt.getSubject())||!fingerprint(bearer).equals(jwt.getClaimAsString("ath")))throw invalid();
            long site=Long.parseLong(jwt.getClaimAsString("siteId"));if(site<=0||jwt.getExpiresAt()==null||!jwt.getExpiresAt().isAfter(clock.instant()))throw invalid();
            return new Verified(site,jwt.getExpiresAt());
        }catch(JwtException|IllegalArgumentException error){throw invalid();}
    }
    private String fingerprint(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private ApiException invalid(){return new ApiException(SiteContextError.INVALID);}
}
