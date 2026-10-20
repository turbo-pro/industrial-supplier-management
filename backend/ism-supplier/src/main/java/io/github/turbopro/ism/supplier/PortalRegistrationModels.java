package io.github.turbopro.ism.supplier;

import jakarta.validation.constraints.*;

public final class PortalRegistrationModels {
    private PortalRegistrationModels() {}
    public record Register(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String invitationToken,
                           @NotBlank @Pattern(regexp="[A-Za-z][A-Za-z0-9_.-]{2,63}") String username,
                           @NotBlank @Size(min=12,max=72) String password) {
        @Override public String toString(){return "PortalRegister[credentials=REDACTED]";}
    }
    public record Registered(String accountId,String tenantId,String supplierId,String username) {}
}
