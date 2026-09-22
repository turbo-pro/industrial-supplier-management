package io.github.turbopro.ism.iam.access;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.authorization.DataScope;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AccessService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AccessMapper mapper;
    private final PasswordEncoder passwords;

    public AccessService(AccessMapper mapper, PasswordEncoder passwords) {
        this.mapper = mapper;
        this.passwords = passwords;
    }

    public List<AccessModels.RoleView> roles() {
        return mapper.roles(tenantId()).stream().map(this::roleView).toList();
    }

    @Transactional
    public AccessModels.RoleView createRole(AccessModels.CreateRole command) {
        long id = id();
        try { mapper.insertRole(tenantId(), id, command.code(), command.name()); }
        catch (DuplicateKeyException exception) { throw new ApiException(AccessErrorCode.DUPLICATE); }
        return roleView(requireRole(id));
    }

    @Transactional
    public AccessModels.RoleView updateRole(long id, AccessModels.UpdateRole command) {
        if (mapper.updateRole(tenantId(), id, command.name(), command.version()) != 1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        return roleView(requireRole(id));
    }

    @Transactional
    public AccessModels.RoleView grantRole(long id, AccessModels.GrantRole command) {
        AccessModels.RoleRow role = requireRole(id);
        if (role.builtIn()) throw new ApiException(AccessErrorCode.BUILT_IN_ROLE);
        long tenantId = tenantId();
        mapper.clearPermissions(tenantId,id); mapper.clearMenus(tenantId,id);
        mapper.clearScopeOrganizations(tenantId,id); mapper.clearScopes(tenantId,id);
        if (!command.permissionCodes().isEmpty()
                && mapper.grantPermissions(tenantId,id,command.permissionCodes()) != command.permissionCodes().size())
            throw new ApiException(AccessErrorCode.INVALID_GRANT,"权限代码不存在");
        if (!command.menuCodes().isEmpty()) {
            if (mapper.grantMenus(tenantId,id,command.menuCodes()) != command.menuCodes().size())
                throw new ApiException(AccessErrorCode.INVALID_GRANT,"菜单代码不存在");
            mapper.grantParentMenus(tenantId,id);
        }
        Set<String> resources = new HashSet<>();
        for (var scope : command.dataScopes()) {
            if (!resources.add(scope.resourceCode())) throw new ApiException(AccessErrorCode.INVALID_GRANT,"资源范围重复");
            DataScope.Type type;
            try { type=DataScope.Type.valueOf(scope.scopeType()); }
            catch (IllegalArgumentException exception) { throw new ApiException(AccessErrorCode.INVALID_GRANT,"数据范围类型不存在"); }
            mapper.grantScope(tenantId,id,scope.resourceCode(),type.name());
            if (type == DataScope.Type.ORGANIZATION_SET) {
                Set<Long> organizationIds=parseIds(scope.organizationIds());
                if (organizationIds.isEmpty() || mapper.grantScopeOrganizations(tenantId,id,scope.resourceCode(),organizationIds)!=organizationIds.size())
                    throw new ApiException(AccessErrorCode.INVALID_GRANT,"组织范围不存在或不属于当前租户");
            } else if (!scope.organizationIds().isEmpty()) throw new ApiException(AccessErrorCode.INVALID_GRANT,"该范围类型不能指定组织");
        }
        return roleView(requireRole(id));
    }

    public List<AccessModels.UserView> users() {
        return mapper.users(tenantId()).stream().map(this::userView).toList();
    }

    @Transactional
    public AccessModels.UserView createUser(AccessModels.CreateUser command) {
        long tenantId=tenantId(), userId=id(), organizationId=parseId(command.primaryOrganizationId());
        try {
            mapper.insertUser(tenantId,userId,command.username(),command.displayName(),passwords.encode(command.initialPassword()));
            if(mapper.insertUserOrganization(tenantId,userId,organizationId)!=1) throw new ApiException(AccessErrorCode.INVALID_GRANT,"主组织不存在");
            mapper.insertUserContext(tenantId,userId,organizationId);
            assignRoles(tenantId,userId,command.roleIds());
        } catch(DuplicateKeyException exception){throw new ApiException(AccessErrorCode.DUPLICATE);}
        return mapper.users(tenantId).stream().filter(row->row.id()==userId).map(this::userView).findFirst().orElseThrow();
    }

    @Transactional
    public void assignUserRoles(long userId, AccessModels.AssignUserRoles command) {
        long tenantId=tenantId();
        assignRoles(tenantId,userId,command.roleIds());
        if(mapper.revokeUserSessions(tenantId,userId)!=1) throw new ApiException(CommonErrorCode.NOT_FOUND);
    }

    private void assignRoles(long tenantId,long userId,Set<String> values){
        Set<Long> ids=parseIds(values); mapper.clearUserRoles(tenantId,userId);
        if(!ids.isEmpty()&&mapper.assignUserRoles(tenantId,userId,ids)!=ids.size()) throw new ApiException(AccessErrorCode.INVALID_GRANT,"角色不存在或已停用");
    }
    private AccessModels.RoleRow requireRole(long id){var row=mapper.role(tenantId(),id);if(row==null)throw new ApiException(CommonErrorCode.NOT_FOUND);return row;}
    private AccessModels.RoleView roleView(AccessModels.RoleRow r){return new AccessModels.RoleView(Long.toString(r.id()),r.roleCode(),r.roleName(),r.builtIn(),r.status(),r.version());}
    private AccessModels.UserView userView(AccessModels.UserRow r){return new AccessModels.UserView(Long.toString(r.id()),r.username(),r.displayName(),r.status(),r.forcePasswordChange(),r.version());}
    private Set<Long> parseIds(Set<String> values){Set<Long> ids=new HashSet<>();for(String value:values)ids.add(parseId(value));return ids;}
    private long parseId(String value){try{long v=Long.parseLong(value);if(v<=0)throw new NumberFormatException();return v;}catch(NumberFormatException e){throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"ID 必须是正整数");}}
    private long tenantId(){return TenantContext.require().tenantId();}
    private static long id(){return RANDOM.nextLong(Long.MAX_VALUE-1)+1;}
}
