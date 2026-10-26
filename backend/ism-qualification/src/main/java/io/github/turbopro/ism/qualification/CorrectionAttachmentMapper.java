package io.github.turbopro.ism.qualification;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper public interface CorrectionAttachmentMapper extends TenantScopedMapper {
    @Select("SELECT COUNT(*) FROM qua_portal_submission WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND qualification_id=#{id}") int ownSubmission(long tenantId,long supplierId,long id);
    @Select("SELECT CAST(id AS CHAR) id,requirement_index,file_name FROM qua_correction_attachment WHERE tenant_id=#{tenantId} AND qualification_id=#{id} ORDER BY requirement_index,id") List<PortalCertificateSubmissionModels.Attachment> list(long tenantId,long id);
    @Select("SELECT file_id,file_name FROM qua_correction_attachment WHERE tenant_id=#{tenantId} AND qualification_id=#{id} AND id=#{attachmentId}") PortalCertificateSubmissionModels.AttachmentFile file(long tenantId,long id,long attachmentId);
}
