package io.github.turbopro.ism.bootstrap;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Platform dispatch query; the tenant-scoped business writes happen only after opening system context. */
@Mapper
public interface ExitReminderDispatchMapper {
    record Candidate(long id,long tenantId,long supplierId,long applicationId) {}

    @Select("""
        SELECT e.id,e.tenant_id,a.supplier_id,e.application_id
        FROM sup_exit_entity e
        JOIN sup_exit_application a ON a.id=e.application_id AND a.tenant_id=e.tenant_id AND a.status='SUBMITTED'
        JOIN iam_tenant t ON t.id=e.tenant_id AND t.status='ACTIVE'
        JOIN cfg_tenant_setting p ON p.tenant_id=e.tenant_id AND p.setting_key='exit.autoReminderEnabled' AND p.setting_value='1'
        WHERE e.id>#{afterId} AND e.state='OPEN' AND e.assignee_id IS NOT NULL
          AND e.due_date < #{today} AND (e.last_reminded_at IS NULL OR e.last_reminded_at <= #{before})
        ORDER BY e.id LIMIT #{size}
        """)
    List<Candidate> candidates(long afterId,LocalDate today,LocalDateTime before,int size);

    @Select("SELECT p.setting_value FROM cfg_tenant_setting p JOIN iam_tenant t ON t.id=p.tenant_id AND t.status='ACTIVE' WHERE p.tenant_id=#{tenantId} AND p.setting_key='exit.autoReminderEnabled' FOR UPDATE")
    String enabled(long tenantId);

    @Select("SELECT p.setting_value FROM cfg_tenant_setting p WHERE p.tenant_id=#{tenantId} AND p.setting_key='exit.autoReminderIntervalHours' FOR UPDATE")
    String intervalHours(long tenantId);
}
