package io.github.turbopro.ism.iam.authorization;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.Set;

@Mapper
public interface TenantNotificationRecipientMapper extends TenantScopedMapper {
    @Select("""
        SELECT DISTINCT u.id FROM iam_user u
        JOIN iam_user_role ur ON ur.tenant_id=u.tenant_id AND ur.user_id=u.id
        JOIN iam_role r ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id
        WHERE u.tenant_id=#{tenantId} AND u.status='ACTIVE' AND u.deleted=0
          AND r.status='ACTIVE' AND r.built_in=1 AND r.role_code='TENANT_ADMIN'
        """)
    Set<Long> administrators(long tenantId);
}
