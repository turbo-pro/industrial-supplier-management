package io.github.turbopro.ism.platform.support;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    record Entry(long requestId, long decisionId, long operatorId, String scopeJson) {}

    @Select("""
            SELECT request_id, decision_id, platform_operator_id AS operator_id,
                   granted_scope_json AS scope_json
            FROM plt_support_session WHERE id=#{id}
            """)
    Entry entry(long id);

    @Update("""
            UPDATE plt_support_session
            SET entry_digest=#{digest}, browser_digest=#{browser}, entry_expires_at=#{deadline}
            WHERE id=#{id} AND status='PENDING' AND entry_digest IS NULL
              AND expires_at > UTC_TIMESTAMP(3)
            """)
    int issue(long id, String digest, String browser, LocalDateTime deadline);

    @Update("""
            UPDATE plt_support_session
            SET status='ACTIVE', activated_at=UTC_TIMESTAMP(3), entry_digest=NULL,
                browser_digest=NULL, entry_expires_at=NULL
            WHERE id=#{id} AND status='PENDING' AND entry_digest=#{digest}
              AND browser_digest=#{browser} AND entry_expires_at > UTC_TIMESTAMP(3)
              AND expires_at > UTC_TIMESTAMP(3)
            """)
    int consume(long id, String digest, String browser);
}
