package io.github.turbopro.ism.qualification;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface PortalQualificationMapper extends TenantScopedMapper {
    @Select("SELECT file_name FROM qua_portal_submission WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND qualification_id=#{id}") String portalFileName(long tenantId,long supplierId,long id);
    String FROM=" FROM qua_supplier_qualification q JOIN qua_qualification_type t ON t.id=q.qualification_type_id AND t.tenant_id=q.tenant_id WHERE q.tenant_id=#{tenantId} AND q.supplier_id=#{supplierId} AND q.status IN ('VALID','REVOKED','SUPERSEDED')";
    String FILTER="<if test='keyword != null'> AND (q.certificate_no LIKE #{keyword} ESCAPE '=' OR t.type_name LIKE #{keyword} ESCAPE '=' OR t.type_code LIKE #{keyword} ESCAPE '=')</if><if test='status != null'> AND "+QualificationMapper.EFFECTIVE_STATUS+"=#{status}</if>";
    String COLUMNS="SELECT CAST(q.id AS CHAR) id,t.type_code,t.type_name,q.certificate_no,q.issuing_authority,q.issue_date,q.effective_date,q.expiry_date,q.permanent_flag permanent,"+QualificationMapper.EFFECTIVE_STATUS+" status,CASE WHEN q.permanent_flag=1 OR q.expiry_date IS NULL THEN NULL ELSE DATEDIFF(q.expiry_date,CURRENT_DATE) END days_until_expiry,q.updated_at,q.version";
    @Select("<script>SELECT COUNT(*)"+FROM+FILTER+"</script>")
    long count(long tenantId,long supplierId,String keyword,String status);
    @Select("<script>"+COLUMNS+FROM+FILTER+" ORDER BY q.updated_at DESC,q.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<PortalQualificationModels.Certificate> list(long tenantId,long supplierId,String keyword,String status,int offset,int size);
    @Select("<script>"+COLUMNS+FROM+" AND q.id=#{id}</script>")
    PortalQualificationModels.Certificate find(long tenantId,long supplierId,long id);
    @Select("SELECT q.file_id"+FROM+" AND q.id=#{id}")
    Long fileId(long tenantId,long supplierId,long id);
}
