package io.github.turbopro.ism.iam.organization;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrganizationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<Type, List<Type>> ALLOWED_CHILDREN = allowedChildren();
    private final OrganizationMapper mapper;

    public OrganizationService(OrganizationMapper mapper) {
        this.mapper = mapper;
    }

    public List<OrganizationModels.OrganizationNode> tree() {
        long tenantId = TenantContext.require().tenantId();
        List<OrganizationModels.OrganizationRow> rows = mapper.list(tenantId);
        Map<Long, List<OrganizationModels.OrganizationRow>> byParent = new HashMap<>();
        for (var row : rows) {
            byParent.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row);
        }
        return nodes(byParent, null);
    }

    @Transactional
    public OrganizationModels.OrganizationNode create(OrganizationModels.CreateOrganization command) {
        long tenantId = TenantContext.require().tenantId();
        long parentId = parseId(command.parentId());
        OrganizationModels.OrganizationRow parent = require(tenantId, parentId);
        Type childType = parseType(command.type());
        Type parentType = parseType(parent.organizationType());
        if (!ALLOWED_CHILDREN.get(parentType).contains(childType)) {
            throw new ApiException(OrganizationErrorCode.INVALID_HIERARCHY);
        }
        long id = id();
        try {
            mapper.insert(tenantId, id, parentId, command.code(), command.name(), childType.name(),
                    command.sortOrder());
            mapper.insertSelf(tenantId, id);
            mapper.insertAncestors(tenantId, id, parentId);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(OrganizationErrorCode.DUPLICATE);
        }
        return node(require(tenantId, id), List.of());
    }

    @Transactional
    public OrganizationModels.OrganizationNode update(long id, OrganizationModels.UpdateOrganization command) {
        long tenantId = TenantContext.require().tenantId();
        if (mapper.update(tenantId, id, command.name(), command.sortOrder(), command.version()) != 1) {
            throw new ApiException(CommonErrorCode.CONFLICT);
        }
        return node(require(tenantId, id), List.of());
    }

    @Transactional
    public void disable(long id, int version) {
        long tenantId = TenantContext.require().tenantId();
        if (mapper.activeChildCount(tenantId, id) > 0) {
            throw new ApiException(OrganizationErrorCode.HAS_ACTIVE_CHILDREN);
        }
        if (mapper.disable(tenantId, id, version) != 1) {
            throw new ApiException(CommonErrorCode.CONFLICT);
        }
    }

    @Transactional
    public OrganizationModels.CurrentOrganization switchTo(String organizationIdValue) {
        long organizationId = parseId(organizationIdValue);
        TenantContext.Identity identity = TenantContext.require();
        OrganizationModels.OrganizationRow target = require(identity.tenantId(), organizationId);
        if (!"ACTIVE".equals(target.status())
                || mapper.canSwitch(identity.tenantId(), identity.actorId(), organizationId) < 1) {
            throw new ApiException(OrganizationErrorCode.NOT_SWITCHABLE);
        }
        mapper.switchContext(identity.tenantId(), identity.actorId(), organizationId);
        return current();
    }

    public OrganizationModels.CurrentOrganization current() {
        TenantContext.Identity identity = TenantContext.require();
        OrganizationModels.OrganizationRow row = mapper.current(identity.tenantId(), identity.actorId());
        if (row == null) {
            throw new ApiException(CommonErrorCode.NOT_FOUND);
        }
        return new OrganizationModels.CurrentOrganization(Long.toString(row.id()), row.organizationCode(),
                row.organizationName(), row.organizationType());
    }

    private OrganizationModels.OrganizationRow require(long tenantId, long id) {
        OrganizationModels.OrganizationRow row = mapper.find(tenantId, id);
        if (row == null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        return row;
    }

    private List<OrganizationModels.OrganizationNode> nodes(
            Map<Long, List<OrganizationModels.OrganizationRow>> byParent, Long parentId) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(row -> node(row, nodes(byParent, row.id())))
                .toList();
    }

    private OrganizationModels.OrganizationNode node(OrganizationModels.OrganizationRow row,
                                                       List<OrganizationModels.OrganizationNode> children) {
        return new OrganizationModels.OrganizationNode(Long.toString(row.id()),
                row.parentId() == null ? null : row.parentId().toString(), row.organizationCode(),
                row.organizationName(), row.organizationType(), row.status(), row.sortOrder(), row.version(), children);
    }

    private long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED, "organizationId 必须是正整数");
        }
    }

    private Type parseType(String value) {
        try {
            return Type.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(OrganizationErrorCode.INVALID_HIERARCHY, "未知组织类型: " + value);
        }
    }

    private static Map<Type, List<Type>> allowedChildren() {
        Map<Type, List<Type>> result = new EnumMap<>(Type.class);
        result.put(Type.HEADQUARTERS, List.of(Type.SUBSIDIARY, Type.SITE, Type.DEPARTMENT));
        result.put(Type.SUBSIDIARY, List.of(Type.SUBSIDIARY, Type.SITE, Type.DEPARTMENT));
        result.put(Type.SITE, List.of(Type.DEPARTMENT));
        result.put(Type.DEPARTMENT, List.of(Type.DEPARTMENT));
        return Map.copyOf(result);
    }

    private static long id() { return RANDOM.nextLong(Long.MAX_VALUE - 1) + 1; }

    enum Type { HEADQUARTERS, SUBSIDIARY, SITE, DEPARTMENT }
}
