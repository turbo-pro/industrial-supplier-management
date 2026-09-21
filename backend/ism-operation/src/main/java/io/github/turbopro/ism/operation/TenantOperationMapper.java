package io.github.turbopro.ism.operation;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface TenantOperationMapper extends TenantScopedMapper {
    @Insert("""
        INSERT INTO sys_audit_event(id,tenant_id,operator_id,acting_user_id,action,object_type,object_id,
          before_summary,after_summary,ip,user_agent,trace_id,occurred_at)
        VALUES(#{id},#{tenantId},#{operatorId},#{actingUserId},#{action},#{objectType},#{objectId},
          CAST(#{beforeSummary} AS JSON),CAST(#{afterSummary} AS JSON),#{ip},#{userAgent},#{traceId},#{occurredAt})
        """)
    int insertAudit(long id, long tenantId, long operatorId, Long actingUserId, String action,
                    String objectType, long objectId, String beforeSummary, String afterSummary,
                    String ip, String userAgent, String traceId, LocalDateTime occurredAt);

    @Insert("""
        INSERT INTO sys_outbox_event(id,event_id,tenant_id,event_type,event_version,aggregate_type,aggregate_id,
          payload,status,retry_count,next_retry_at,occurred_at,trace_id)
        VALUES(#{id},#{eventId},#{tenantId},#{eventType},#{eventVersion},#{aggregateType},#{aggregateId},
          CAST(#{payload} AS JSON),'PENDING',0,#{now},#{now},#{traceId})
        """)
    int insertOutbox(long id, String eventId, long tenantId, String eventType, int eventVersion,
                     String aggregateType, long aggregateId, String payload, LocalDateTime now, String traceId);

    @Insert("""
        INSERT INTO sys_idempotency_record(id,tenant_id,principal_id,operation_code,idempotency_key,
          request_hash,status,expires_at)
        VALUES(#{id},#{tenantId},#{principalId},#{operationCode},#{idempotencyKey},#{requestHash},
          'PROCESSING',#{expiresAt})
        """)
    int insertIdempotency(long id, long tenantId, long principalId, String operationCode,
                          String idempotencyKey, String requestHash, LocalDateTime expiresAt);

    @Select("""
        SELECT id,tenant_id,principal_id,operation_code,idempotency_key,request_hash,status,
          response_status,response_body
        FROM sys_idempotency_record
        WHERE tenant_id=#{tenantId} AND principal_id=#{principalId} AND operation_code=#{operationCode}
          AND idempotency_key=#{idempotencyKey}
        """)
    OperationModels.IdempotencyRecord findIdempotency(long tenantId, long principalId,
                                                       String operationCode, String idempotencyKey);

    @Update("""
        UPDATE sys_idempotency_record SET status='COMPLETED',response_status=#{responseStatus},
          response_body=CAST(#{responseBody} AS JSON)
        WHERE id=#{id} AND tenant_id=#{tenantId} AND status='PROCESSING'
        """)
    int completeIdempotency(long id, long tenantId, int responseStatus, String responseBody);

    @Insert("""
        INSERT INTO ops_async_task(id,tenant_id,task_no,task_type,requester_id,request_payload,status,priority)
        VALUES(#{id},#{tenantId},#{taskNo},#{taskType},#{requesterId},CAST(#{payload} AS JSON),'QUEUED',#{priority})
        """)
    int insertTask(long id, long tenantId, String taskNo, String taskType, long requesterId,
                   String payload, int priority);
}
