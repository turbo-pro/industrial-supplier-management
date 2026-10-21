package io.github.turbopro.ism.iam.portal;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public final class PortalAuthModels {
    private PortalAuthModels() {}
    public record Login(@NotBlank @Size(max=64) String tenantCode,
                        @NotBlank @Pattern(regexp="[A-Za-z][A-Za-z0-9_.-]{2,63}") String username,
                        @NotBlank @Size(max=72) String password) {
        @Override public String toString(){return "PortalLogin[credentials=REDACTED]";}
    }
    public record User(String accountId,String tenantId,String supplierId,String username,String displayName) {}
    public record Session(User user,String accessToken,LocalDateTime expiresAt) {
        @Override public String toString(){return "PortalSession[token=REDACTED]";}
    }
    public record Principal(long accountId,long tenantId,long supplierId,long sessionId,String username,String displayName) {
        public User user(){return new User(Long.toString(accountId),Long.toString(tenantId),Long.toString(supplierId),username,displayName);}
    }
    public record Account(long id,long supplierId,String username,String displayName,String passwordHash,
                          String status,int tokenVersion,int failedCount,boolean locked) {}
    public record SessionRow(long id,long accountId,int tokenVersion,boolean valid) {}
}
