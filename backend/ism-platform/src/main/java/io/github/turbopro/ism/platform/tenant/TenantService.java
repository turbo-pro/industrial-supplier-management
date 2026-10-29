package io.github.turbopro.ism.platform.tenant;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.Locale;

@Service
public class TenantService {
    private final TenantMapper mapper;
    private final OperationIdGenerator ids;
    private final PasswordEncoder passwords;
    private final io.github.turbopro.ism.iam.organization.OrganizationProvisioner organizations;

    public TenantService(TenantMapper mapper, OperationIdGenerator ids, PasswordEncoder passwords, io.github.turbopro.ism.iam.organization.OrganizationProvisioner organizations) {
        this.mapper = mapper;
        this.ids = ids;
        this.passwords = passwords;
        this.organizations = organizations;
    }

    public List<TenantModels.TenantView> list() {
        return mapper.list().stream().map(this::view).toList();
    }

    public TenantModels.TenantView get(long id) {
        TenantModels.TenantRow row = mapper.find(id);
        if (row == null) {
            throw new ApiException(CommonErrorCode.NOT_FOUND);
        }
        return view(row);
    }

    @Transactional
    public TenantModels.TenantView create(TenantModels.CreateTenant command) {
        validateLocaleAndTimezone(command.locale(), command.timezone());
        long packageVersionId = parseId(command.packageVersionId());
        if (mapper.publishedPackageExists(packageVersionId) != 1) {
            throw new ApiException(TenantErrorCode.PACKAGE_UNAVAILABLE);
        }
        long tenantId = ids.nextId();
        try {
            mapper.insert(tenantId, command.code(), command.name(), command.timezone(), command.locale(),
                    packageVersionId);
            mapper.insertSubscription(ids.nextId(), tenantId, packageVersionId, utcNow());
        } catch (DuplicateKeyException exception) {
            throw new ApiException(TenantErrorCode.DUPLICATE);
        }
        return get(tenantId);
    }

    @Transactional
    public TenantModels.TenantView update(long id, TenantModels.UpdateTenant command) {
        validateLocaleAndTimezone(command.locale(), command.timezone());
        if (mapper.update(id, command.name(), command.timezone(), command.locale(), command.version()) != 1) {
            throw new ApiException(CommonErrorCode.CONFLICT, "租户已注销或版本已变化");
        }
        return get(id);
    }

    @Transactional
    public TenantModels.TenantView initialize(long id, TenantModels.InitializeTenant command) {
        TenantModels.TenantRow tenant = requireState(id, "PROVISIONING");
        if (!"PENDING".equals(tenant.initializationStatus())) {
            throw new ApiException(TenantErrorCode.INVALID_STATE);
        }
        validateStructure(command);
        try {
            mapper.insertIamTenant(tenant.id(), tenant.tenantCode(), tenant.tenantName(), tenant.timezone(),
                    tenant.locale());
            long administratorId = ids.nextId();
            var structure=organizations.initialize(tenant.id(),administratorId,ids.nextId(),ids.nextId(),command.operationMode(),command.headquartersName(),command.siteName());
            long headquartersId=structure.primaryOrganizationId();
            if(mapper.operationStructure(id,command.operationMode(),command.siteLabel(),structure.defaultSiteId())!=1)
                throw new ApiException(CommonErrorCode.CONFLICT);
            mapper.insertAdmin(administratorId, tenant.id(), command.adminUsername(), command.adminDisplayName(),
                    passwords.encode(command.initialPassword()));
            mapper.insertAdminOrganization(tenant.id(), administratorId, headquartersId);
            mapper.insertAdminContext(tenant.id(), administratorId, headquartersId);
            long administratorRoleId = ids.nextId();
            mapper.insertTenantAdminRole(tenant.id(), administratorRoleId);
            mapper.insertAdminRole(tenant.id(), administratorId, administratorRoleId);
            mapper.grantAdminPermissions(tenant.id(), administratorRoleId);
            mapper.grantAdminMenus(tenant.id(), administratorRoleId);
            mapper.grantAdminDataScopes(tenant.id(), administratorRoleId);
            if (mapper.markReady(id, utcNow()) != 1) {
                throw new ApiException(TenantErrorCode.INVALID_STATE);
            }
        } catch (DuplicateKeyException exception) {
            throw new ApiException(TenantErrorCode.DUPLICATE, "租户身份域或首管理员已存在");
        }
        return get(id);
    }

    @Transactional
    public TenantModels.TenantView suspend(long id) {
        return transition(id, "ACTIVE", "SUSPENDED", "SUSPENDED", "SUSPENDED");
    }

    @Transactional
    public TenantModels.TenantView resume(long id) {
        return transition(id, "SUSPENDED", "ACTIVE", "ACTIVE", "ACTIVE");
    }

    @Transactional
    public TenantModels.TenantView cancel(long id) {
        TenantModels.TenantView result = transition(id, "SUSPENDED", "CANCELLED", "CANCELLED", "DISABLED");
        return result;
    }

    private TenantModels.TenantView transition(long id, String expected, String target,
                                                String iamStatus, String userStatus) {
        requireState(id, expected);
        if (mapper.transition(id, expected, target) != 1) {
            throw new ApiException(TenantErrorCode.INVALID_STATE);
        }
        mapper.updateIamStatus(id, iamStatus);
        mapper.updateUserStatus(id, userStatus);
        return get(id);
    }

    private TenantModels.TenantRow requireState(long id, String status) {
        TenantModels.TenantRow tenant = mapper.find(id);
        if (tenant == null) {
            throw new ApiException(CommonErrorCode.NOT_FOUND);
        }
        if (!status.equals(tenant.status())) {
            throw new ApiException(TenantErrorCode.INVALID_STATE);
        }
        return tenant;
    }

    private TenantModels.TenantView view(TenantModels.TenantRow row) {
        return new TenantModels.TenantView(Long.toString(row.id()), row.tenantCode(), row.tenantName(),
                row.status(), row.initializationStatus(), row.timezone(), row.locale(),
                row.packageVersionId() == null ? null : row.packageVersionId().toString(),
                row.version(), row.initializedAt(), row.operationMode(), row.siteLabel(), row.defaultSiteId()==null?null:row.defaultSiteId().toString());
    }

    private void validateStructure(TenantModels.InitializeTenant command){
        String mode=command.operationMode();
        if(mode==null){
            if(command.headquartersName()==null||command.headquartersName().isBlank())throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
            return;
        }
        if(!io.github.turbopro.ism.iam.auth.PasswordPolicy.isStrong(command.initialPassword())
            ||command.initialPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"初始密码须包含大小写字母、数字、符号且至少12位，UTF-8不超过72字节");
        if(!java.util.Set.of("SINGLE_SITE","MULTI_SITE").contains(mode)
            || command.siteLabel()==null||command.siteLabel().isBlank()||command.siteLabel().length()>20
            || command.siteName()==null||command.siteName().isBlank()||command.siteName().length()>200
            || ("MULTI_SITE".equals(mode)&&(command.headquartersName()==null||command.headquartersName().isBlank())))
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"请选择运营模式并填写场站名称；多场站模式须填写总部名称");
    }

    private long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED, "packageVersionId 必须是正整数");
        }
    }

    private void validateLocaleAndTimezone(String locale, String timezone) {
        try {
            java.time.ZoneId.of(timezone);
        } catch (ZoneRulesException exception) {
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED, "时区无效");
        }
        if (Locale.forLanguageTag(locale).getLanguage().isBlank()) {
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED, "语言区域无效");
        }
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
