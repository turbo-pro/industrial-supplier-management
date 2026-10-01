package io.github.turbopro.ism.bootstrap;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExitAccessReminderDispatchMapper {
    record Candidate(long id,long tenantId,long supplierId,long applicationId) {}

    @Select("""
        SELECT r.id,r.tenant_id,r.supplier_id,r.application_id
        FROM sup_exit_access_recovery_task r
        JOIN sup_exit_application a ON a.id=r.application_id AND a.tenant_id=r.tenant_id AND a.status='BUSINESS_CLOSED'
        JOIN sup_exit_result x ON x.application_id=a.id AND x.tenant_id=a.tenant_id
        JOIN iam_tenant t ON t.id=r.tenant_id AND t.status='ACTIVE'
        JOIN cfg_tenant_setting p ON p.tenant_id=r.tenant_id AND p.setting_key='exit.accessAutoReminderEnabled' AND p.setting_value='1'
        WHERE r.id>#{afterId} AND r.assignee_id IS NOT NULL AND r.due_date < #{today}
          AND (r.last_reminded_at IS NULL OR r.last_reminded_at <= #{before})
        ORDER BY r.id LIMIT #{size}
        """)
    List<Candidate> candidates(long afterId,LocalDate today,LocalDateTime before,int size);

    @Select("SELECT p.setting_value FROM cfg_tenant_setting p JOIN iam_tenant t ON t.id=p.tenant_id AND t.status='ACTIVE' WHERE p.tenant_id=#{tenantId} AND p.setting_key='exit.accessAutoReminderEnabled' FOR UPDATE")
    String enabled(long tenantId);

    @Select("SELECT p.setting_value FROM cfg_tenant_setting p WHERE p.tenant_id=#{tenantId} AND p.setting_key='exit.accessAutoReminderIntervalHours' FOR UPDATE")
    String intervalHours(long tenantId);
}
