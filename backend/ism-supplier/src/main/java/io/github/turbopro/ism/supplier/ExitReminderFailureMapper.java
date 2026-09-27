package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExitReminderFailureMapper extends TenantScopedMapper {
    @Insert("""
        INSERT INTO sup_exit_reminder_failure(tenant_id,entity_id,application_id,supplier_id,failure_count,reason_code,status,first_failed_at,last_failed_at)
        SELECT e.tenant_id,e.id,e.application_id,a.supplier_id,1,#{reasonCode},'FAILED',#{now},#{now}
        FROM sup_exit_entity e JOIN sup_exit_application a ON a.id=e.application_id AND a.tenant_id=e.tenant_id
        WHERE e.tenant_id=#{tenantId} AND e.id=#{entityId} AND e.application_id=#{applicationId} AND a.supplier_id=#{supplierId}
        ON DUPLICATE KEY UPDATE failure_count=IF(failure_count>=2147483647,2147483647,failure_count+1),reason_code=VALUES(reason_code),status='FAILED',last_failed_at=VALUES(last_failed_at),resolved_at=NULL
        """)
    int record(long tenantId,long supplierId,long applicationId,long entityId,String reasonCode,LocalDateTime now);

    @Update("UPDATE sup_exit_reminder_failure SET status='DELIVERED',resolved_at=#{now} WHERE tenant_id=#{tenantId} AND entity_id=#{entityId} AND application_id=#{applicationId} AND supplier_id=#{supplierId} AND status='FAILED'")
    int resolve(long tenantId,long supplierId,long applicationId,long entityId,LocalDateTime now);

    String FROM=" FROM sup_exit_reminder_failure f JOIN sup_exit_entity e ON e.id=f.entity_id AND e.tenant_id=f.tenant_id AND e.application_id=f.application_id JOIN sup_exit_application a ON a.id=f.application_id AND a.tenant_id=f.tenant_id AND a.supplier_id=f.supplier_id WHERE f.tenant_id=#{tenantId} AND f.supplier_id=#{supplierId}";
    @Select("SELECT COUNT(*)"+FROM)
    long count(long tenantId,long supplierId);
    @Select("SELECT f.entity_id,f.application_id,f.supplier_id,f.failure_count,f.reason_code,f.status,f.first_failed_at,f.last_failed_at,f.resolved_at,e.state entity_state,a.status application_status,e.due_date"+FROM+" ORDER BY f.last_failed_at DESC,f.entity_id DESC LIMIT #{size} OFFSET #{offset}")
    List<ExitReminderFailureModels.Row> list(long tenantId,long supplierId,int offset,int size);
}
