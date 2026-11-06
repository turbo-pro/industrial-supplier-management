package io.github.turbopro.ism.platform.support;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SupportSessionMapper {
    @Insert("""
            INSERT INTO plt_support_session
                (id, request_id, decision_id, platform_operator_id, target_tenant_id,
                 effective_role_id, granted_scope_json, created_at, expires_at)
            VALUES (#{id}, #{requestId}, #{decisionId}, #{operatorId}, #{tenantId},
                    #{roleId}, #{scope}, #{createdAt}, #{expiresAt})
            """)
    int insert(long id, long requestId, long decisionId, long operatorId, long tenantId,
               long roleId, String scope, LocalDateTime createdAt, LocalDateTime expiresAt);
}
