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
    @Select("<script>SELECT e.id,e.application_id,a.supplier_id,s.supplier_code,s.supplier_name,s.organization_id,e.check_code,e.source_id,e.route,e.note,e.assigned_at,e.checked_at,e.version,a.version application_version"+FROM+SCOPE+FILTER+" ORDER BY e.assigned_at DESC,e.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<ExitTaskModels.Row> list(long tenantId,long actorId,String scopeType,Set<Long> organizationIds,String keyword,String code,int offset,int size);
}
