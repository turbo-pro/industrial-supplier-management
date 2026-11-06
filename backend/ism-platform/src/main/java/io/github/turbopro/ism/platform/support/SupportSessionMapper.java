package io.github.turbopro.ism.platform.support;

import java.time.LocalDateTime;
import java.util.List;
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

    record Summary(long id, String state, LocalDateTime createdAt, LocalDateTime expiresAt,
                   LocalDateTime revokedAt) {}

    @Select("""
            SELECT created_at FROM plt_support_session
            WHERE request_id=#{requestId} AND platform_operator_id=#{operatorId} AND id=#{before}
            """)
    LocalDateTime cursorTime(long requestId, long operatorId, long before);

    @Select("""
            <script>
            SELECT id, CASE WHEN status IN ('ACTIVE','PENDING') AND expires_at &lt;= UTC_TIMESTAMP(3)
                THEN 'EXPIRED' ELSE status END AS state, created_at, expires_at, revoked_at
            FROM plt_support_session WHERE request_id=#{requestId} AND platform_operator_id=#{operatorId}
            <if test="before != null">
              AND (created_at &lt; #{cursorTime} OR (created_at = #{cursorTime} AND id &lt; #{before}))
            </if>
            ORDER BY created_at DESC, id DESC LIMIT 21
            </script>
            """)
    List<Summary> listOwn(long requestId, long operatorId, Long before, LocalDateTime cursorTime);

    @Select("SELECT status FROM plt_support_session WHERE id=#{id} FOR UPDATE")
    String lockState(long id);

    record AuditSummary(String id, String requestId, String operatorId, String tenantId,
                        String state, LocalDateTime createdAt, LocalDateTime expiresAt, LocalDateTime revokedAt) {}

    @Select("""
            SELECT CAST(id AS CHAR) AS id, CAST(request_id AS CHAR) AS request_id,
              CAST(platform_operator_id AS CHAR) AS operator_id, CAST(target_tenant_id AS CHAR) AS tenant_id,
              CASE WHEN status IN ('ACTIVE','PENDING') AND expires_at <= UTC_TIMESTAMP(3)
                THEN 'EXPIRED' ELSE status END AS state, created_at, expires_at, revoked_at
            FROM plt_support_session ORDER BY created_at DESC,id DESC LIMIT 21 OFFSET #{offset}
            """)
    List<AuditSummary> auditSessions(int offset);

    record Entry(long requestId, long decisionId, long operatorId, String scopeJson) {}

    @Select("""
            SELECT request_id, decision_id, platform_operator_id AS operator_id,
                   granted_scope_json AS scope_json
            FROM plt_support_session WHERE id=#{id}
            """)
    Entry entry(long id);

    @Select("""
            SELECT request_id, decision_id, platform_operator_id AS operator_id, granted_scope_json AS scope_json
            FROM plt_support_session WHERE id=#{id} AND status='PENDING' AND entry_digest=#{digest}
              AND browser_digest=#{browser} AND entry_expires_at > UTC_TIMESTAMP(3) AND expires_at > UTC_TIMESTAMP(3)
            """)
    Entry entryCandidate(long id, String digest, String browser);

    @Select("""
            SELECT request_id, decision_id, platform_operator_id AS operator_id, granted_scope_json AS scope_json
            FROM plt_support_session WHERE id=#{id} AND status='ACTIVE' AND access_digest=#{digest}
              AND expires_at > UTC_TIMESTAMP(3)
            """)
    Entry workspaceCandidate(long id, String digest);

    @Update("""
            UPDATE plt_support_session
            SET entry_digest=#{digest}, browser_digest=#{browser}, entry_expires_at=#{deadline}
            WHERE id=#{id} AND status='PENDING' AND entry_digest IS NULL
              AND expires_at > UTC_TIMESTAMP(3)
            """)
    int issue(long id, String digest, String browser, LocalDateTime deadline);

    @Update("""
            UPDATE plt_support_session
            SET status='ACTIVE', activated_at=UTC_TIMESTAMP(3), access_digest=#{access}, entry_digest=NULL,
                browser_digest=NULL, entry_expires_at=NULL
            WHERE id=#{id} AND status='PENDING' AND entry_digest=#{digest}
              AND browser_digest=#{browser} AND entry_expires_at > UTC_TIMESTAMP(3)
              AND expires_at > UTC_TIMESTAMP(3)
            """)
    int consume(long id, String digest, String browser, String access);

    @Select("""
            SELECT id FROM plt_support_session
            WHERE id=#{id} AND target_tenant_id=#{tenantId} AND status='ACTIVE'
              AND access_digest=#{digest} AND expires_at > UTC_TIMESTAMP(3)
            FOR UPDATE
            """)
    Long lockActive(long id, long tenantId, String digest);

    @Update("""
            UPDATE plt_support_session
            SET status='REVOKED', revoked_at=UTC_TIMESTAMP(3), access_digest=NULL,
                entry_digest=NULL, browser_digest=NULL, entry_expires_at=NULL
            WHERE id=#{id} AND platform_operator_id=#{operatorId} AND status IN ('ACTIVE','PENDING')
            """)
    int revoke(long id, long operatorId);

    @Select("""
            SELECT id FROM plt_support_session
            WHERE id=#{id} AND platform_operator_id=#{operatorId} AND status='REVOKED'
            FOR UPDATE
            """)
    Long lockRevoked(long id, long operatorId);
}
