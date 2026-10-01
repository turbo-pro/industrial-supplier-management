package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Set;

@Mapper
public interface ExitTaskMapper extends TenantScopedMapper {
    String FROM=" FROM sup_exit_entity e JOIN sup_exit_application a ON a.id=e.application_id AND a.tenant_id=e.tenant_id JOIN sup_supplier s ON s.id=a.supplier_id AND s.tenant_id=a.tenant_id WHERE e.tenant_id=#{tenantId} AND e.assignee_id=#{actorId} AND e.state='OPEN' AND a.status='SUBMITTED' AND s.deleted=0";
    String FILTER="<if test='code != null'> AND e.check_code=#{code}</if><if test='keyword != null'> AND (s.supplier_code LIKE #{keyword} ESCAPE '=' OR s.supplier_name LIKE #{keyword} ESCAPE '=')</if>";
    String SCOPE="<choose><when test=\"scopeType == 'TENANT_ALL'\"></when><when test=\"scopeType == 'ORGANIZATION_SET' and !organizationIds.isEmpty()\"> AND s.organization_id IN <foreach collection='organizationIds' item='org' open='(' separator=',' close=')'>#{org}</foreach></when><when test=\"scopeType == 'CREATED' or scopeType == 'OWNED'\"> AND s.created_by=#{actorId}</when><otherwise> AND 1=0</otherwise></choose>";
    @Select("<script>SELECT COUNT(*)"+FROM+SCOPE+FILTER+"</script>")
    long count(long tenantId,long actorId,String scopeType,Set<Long> organizationIds,String keyword,String code);
    @Select("<script>SELECT e.id,e.application_id,a.supplier_id,s.supplier_code,s.supplier_name,s.organization_id,e.check_code,e.source_id,e.route,e.note,e.assigned_at,e.checked_at,e.version,a.version application_version,e.due_date,e.last_reminded_at"+FROM+SCOPE+FILTER+" ORDER BY e.assigned_at DESC,e.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<ExitTaskModels.Row> list(long tenantId,long actorId,String scopeType,Set<Long> organizationIds,String keyword,String code,int offset,int size);
    String MONITOR_FROM=" FROM sup_exit_entity e JOIN sup_exit_application a ON a.id=e.application_id AND a.tenant_id=e.tenant_id JOIN sup_supplier s ON s.id=a.supplier_id AND s.tenant_id=a.tenant_id WHERE e.tenant_id=#{tenantId} AND e.state='OPEN' AND a.status='SUBMITTED' AND s.deleted=0";
    String MONITOR_FILTER="<if test='code != null'> AND e.check_code=#{code}</if><if test='keyword != null'> AND (s.supplier_code LIKE #{keyword} ESCAPE '=' OR s.supplier_name LIKE #{keyword} ESCAPE '=')</if><if test='unassignedOnly'> AND e.assignee_id IS NULL</if><if test='overdueOnly'> AND e.due_date &lt; #{today}</if>";
    String MONITOR_SCOPE="<choose><when test=\"scopeType == 'TENANT_ALL'\"></when><when test=\"scopeType == 'ORGANIZATION_SET' and !organizationIds.isEmpty()\"> AND s.organization_id IN <foreach collection='organizationIds' item='org' open='(' separator=',' close=')'>#{org}</foreach></when><when test=\"scopeType == 'CREATED' or scopeType == 'OWNED'\"> AND s.created_by=#{actorId}</when><otherwise> AND 1=0</otherwise></choose>";
    @Select("<script>SELECT COUNT(*)"+MONITOR_FROM+MONITOR_SCOPE+MONITOR_FILTER+"</script>")
    long monitorCount(long tenantId,long actorId,String scopeType,Set<Long> organizationIds,String keyword,String code,boolean unassignedOnly,boolean overdueOnly,java.time.LocalDate today);
    @Select("<script>SELECT e.id,e.application_id,a.supplier_id,s.supplier_code,s.supplier_name,s.organization_id,e.check_code,e.source_id,e.route,e.note,e.assignee_id,e.assigned_at,e.checked_at,e.due_date,e.last_reminded_at"+MONITOR_FROM+MONITOR_SCOPE+MONITOR_FILTER+" ORDER BY CASE WHEN e.due_date &lt; #{today} THEN 0 WHEN e.assignee_id IS NULL THEN 1 ELSE 2 END,e.due_date,e.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<ExitTaskModels.MonitorRow> monitorList(long tenantId,long actorId,String scopeType,Set<Long> organizationIds,String keyword,String code,boolean unassignedOnly,boolean overdueOnly,java.time.LocalDate today,int offset,int size);
    String ACCESS_FROM=" FROM sup_exit_access_recovery_task t JOIN sup_exit_application a ON a.id=t.application_id AND a.tenant_id=t.tenant_id AND a.supplier_id=t.supplier_id JOIN sup_supplier s ON s.id=t.supplier_id AND s.tenant_id=t.tenant_id WHERE t.tenant_id=#{tenantId} AND t.assignee_id=#{actorId} AND a.status='BUSINESS_CLOSED' AND s.deleted=0 AND t.status IN ('DISCOVERY_REQUIRED','DISCOVERY_RECORDED')";
    String ACCESS_FILTER="<if test='channel != null'> AND t.channel=#{channel}</if><if test='keyword != null'> AND (s.supplier_code LIKE #{keyword} ESCAPE '=' OR s.supplier_name LIKE #{keyword} ESCAPE '=')</if>";
    String ACCESS_SCOPE="<choose><when test=\"scopeType == 'TENANT_ALL'\"></when><when test=\"scopeType == 'ORGANIZATION_SET' and !organizationIds.isEmpty()\"> AND s.organization_id IN <foreach collection='organizationIds' item='org' open='(' separator=',' close=')'>#{org}</foreach></when><when test=\"scopeType == 'CREATED' or scopeType == 'OWNED'\"> AND s.created_by=#{actorId}</when><otherwise> AND 1=0</otherwise></choose>";
    @Select("<script>SELECT COUNT(*)"+ACCESS_FROM+ACCESS_SCOPE+ACCESS_FILTER+"</script>")
    long accessCount(long tenantId,long actorId,String scopeType,Set<Long> organizationIds,String keyword,String channel);
    @Select("<script>SELECT t.id,t.application_id,t.supplier_id,s.supplier_code,s.supplier_name,s.organization_id,t.channel,t.status,t.finding,t.assignment_note,t.due_date,t.assigned_at,t.version"+ACCESS_FROM+ACCESS_SCOPE+ACCESS_FILTER+" ORDER BY t.assigned_at DESC,t.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<ExitTaskModels.AccessRow> accessList(long tenantId,long actorId,String scopeType,Set<Long> organizationIds,String keyword,String channel,int offset,int size);
}
