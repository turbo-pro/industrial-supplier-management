package io.github.turbopro.ism.platform.branding;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Service
public class PlatformBrandingService {
    private final PlatformBrandingMapper mapper;
    private final AuditService audit;

    public PlatformBrandingService(PlatformBrandingMapper mapper, AuditService audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    public PlatformBrandingModels.Branding publicBranding() {
        return branding(mapper.get());
    }

    public PlatformBrandingModels.Settings settings() {
        require("platform:branding:view");
        return settings(mapper.get());
    }

    @Transactional
    public PlatformBrandingModels.Settings save(PlatformBrandingModels.Save command) {
        require("platform:branding:manage");
        var old = mapper.lock();
        if (command.version() != old.version()) {
            throw new ApiException(CommonErrorCode.CONFLICT, "平台品牌配置已变化，请重新读取");
        }
        String logo = imageUrl(command.logoUrl());
        String icon = imageUrl(command.faviconUrl());
        if (mapper.save(command.systemName().trim(), logo, icon, command.footerText().trim(),
                command.version(), TenantContext.require().actorId()) != 1) {
            throw new ApiException(CommonErrorCode.CONFLICT);
        }
        var result = mapper.get();
        audit.append(new AuditService.AuditCommand("PLATFORM_BRANDING_UPDATE", "PLATFORM_BRANDING",
                1L, null, branding(old), branding(result), null, null));
        return settings(result);
    }

    private PlatformBrandingModels.Settings settings(PlatformBrandingModels.Row row) {
        return new PlatformBrandingModels.Settings(branding(row), row.version(),
                AuthorizationContext.require().hasAction("platform:branding:manage"));
    }

    private PlatformBrandingModels.Branding branding(PlatformBrandingModels.Row row) {
        return new PlatformBrandingModels.Branding(row.systemName(), row.logoUrl(),
                row.faviconUrl(), row.footerText());
    }

    private void require(String permission) {
        if (TenantContext.require().tenantId() != 0
                || !AuthorizationContext.require().hasAction(permission)) {
            throw new ApiException(CommonErrorCode.FORBIDDEN);
        }
    }

    private String imageUrl(String value) {
        String url = value.trim();
        if (url.isEmpty()) return "";
        try {
            if (url.contains("\\") || url.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException();
            }
            var uri = URI.create(url);
            if (url.startsWith("/") && !url.startsWith("//")) return url;
            if (uri.getScheme() == null
                    || !Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
            return url;
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED,
                    "图片地址需为站内绝对路径或不含账号密码的HTTP(S)地址");
        }
    }
}
