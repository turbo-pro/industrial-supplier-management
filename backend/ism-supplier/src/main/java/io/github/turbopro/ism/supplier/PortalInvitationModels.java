package io.github.turbopro.ism.supplier;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class PortalInvitationModels {
    private PortalInvitationModels() {}
    public record Issue(@NotNull @Min(0) Integer expectedGeneration,
                        @NotBlank @Size(max=100) String recipientName,
                        @NotBlank @Email @Size(max=254) String recipientEmail,
                        @Min(1) @Max(168) int validHours) {}
    public record Revoke(@NotNull @Min(0) Integer version,@NotBlank @Size(max=500) String reason) {}
    public record Invitation(String id,int generation,String recipientName,String recipientEmail,
                             String status,LocalDateTime expiresAt,LocalDateTime createdAt,
                             LocalDateTime revokedAt,String revokeReason,int version) {}
    public record History(int latestGeneration,boolean canManage,List<Invitation> items) {}
    public record Issued(Invitation invitation,String token) {}
}
