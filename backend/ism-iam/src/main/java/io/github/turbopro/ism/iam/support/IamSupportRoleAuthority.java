package io.github.turbopro.ism.iam.support;
import io.github.turbopro.ism.common.api.support.SupportRoleAuthority;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.iam.authorization.TenantModuleEntitlements;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class IamSupportRoleAuthority implements SupportRoleAuthority {
 private final SupportRoleMapper mapper;private final TenantModuleEntitlements modules;
 public IamSupportRoleAuthority(SupportRoleMapper mapper,TenantModuleEntitlements modules){this.mapper=mapper;this.modules=modules;}
 public List<Role> options(long tenant){check(tenant);try(var scope=TenantContext.openSystem(tenant)){return mapper.roles(tenant).stream().map(r->resolve(tenant,r.id())).filter(Objects::nonNull).toList();}}
 public Role resolve(long tenant,long id){check(tenant);try(var scope=TenantContext.openSystem(tenant)){var r=mapper.role(tenant,id);if(r==null||!"ACTIVE".equals(r.status())||mapper.readPermissions(tenant,id).isEmpty()||!modules.currentEnabledModules(tenant).contains("SUPPLIER"))return null;String type=mapper.scope(tenant,id);if("TENANT_ALL".equals(type))return new Role(Long.toString(id),r.roleName(),r.version(),true,Set.of());if(!"ORGANIZATION_SET".equals(type))return null;var orgs=new TreeSet<>(mapper.organizations(tenant,id));return orgs.isEmpty()?null:new Role(Long.toString(id),r.roleName(),r.version(),false,Set.copyOf(orgs));}}
 private void check(long tenant){var caller=TenantContext.require();if(tenant<=0||caller.tenantId()!=0&&caller.tenantId()!=tenant)throw new ApiException(CommonErrorCode.FORBIDDEN);}
}
