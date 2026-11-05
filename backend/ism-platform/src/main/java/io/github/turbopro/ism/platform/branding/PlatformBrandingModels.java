package io.github.turbopro.ism.platform.branding;

import jakarta.validation.constraints.*;

public final class PlatformBrandingModels {
    private PlatformBrandingModels() {}

    public record Branding(String systemName, String logoUrl, String faviconUrl, String footerText) {}
    public record Settings(Branding branding, int version, boolean canManage) {}
    public record Row(String systemName, String logoUrl, String faviconUrl, String footerText, int version) {}
    public record Save(@NotBlank @Size(max = 100) String systemName,
                       @NotNull @Size(max = 2000) String logoUrl,
                       @NotNull @Size(max = 2000) String faviconUrl,
                       @NotNull @Size(max = 300) String footerText,
                       @NotNull @PositiveOrZero Integer version) {}
}
