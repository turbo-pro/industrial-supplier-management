package io.github.turbopro.ism.qualification;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext;
import io.github.turbopro.ism.common.infrastructure.portal.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.io.IOException;

@Service public class CorrectionAttachmentService {
    private final CorrectionAttachmentMapper mapper;private final QualificationService qualifications;private final PortalWriteGuard guard;private final PortalModuleAccessGate modules;private final QualificationFileContent files;private final QualificationFileReference fileReferences;private final AuditService audit;
    public CorrectionAttachmentService(CorrectionAttachmentMapper mapper,QualificationService qualifications,PortalWriteGuard guard,PortalModuleAccessGate modules,QualificationFileContent files,AuditService audit,QualificationFileReference fileReferences){this.mapper=mapper;this.qualifications=qualifications;this.guard=guard;this.modules=modules;this.files=files;this.audit=audit;this.fileReferences=fileReferences;}
    @Transactional(readOnly=true) public List<PortalCertificateSubmissionModels.Attachment> portalList(long id){var i=portal();own(i,id);return mapper.list(i.tenantId(),id);}
    @Transactional(readOnly=true) public List<PortalCertificateSubmissionModels.Attachment> employeeList(long id){qualifications.get(id);return mapper.list(TenantContext.require().tenantId(),id);}
    @Transactional public QualificationFileContent.Download portalDownload(long id,long attachmentId){guard.lockCurrent();var i=portal();own(i,id);return open(i.tenantId(),id,attachmentId,"PORTAL_CORRECTION_ATTACHMENT_OPEN");}
    @Transactional public QualificationFileContent.Download employeeDownload(long id,long attachmentId){if(!AuthorizationContext.require().hasAction("supplier:qualification:view")||!AuthorizationContext.require().hasAction("resource:file:download"))throw new ApiException(CommonErrorCode.FORBIDDEN);qualifications.get(id);return open(TenantContext.require().tenantId(),id,attachmentId,"QUALIFICATION_CORRECTION_ATTACHMENT_OPEN");}
    private PortalIdentityContext.Identity portal(){var i=PortalIdentityContext.require();modules.requireAvailable("QUALIFICATION");return i;}
    private void own(PortalIdentityContext.Identity i,long id){if(mapper.ownSubmission(i.tenantId(),i.supplierId(),id)!=1)throw new ApiException(CommonErrorCode.NOT_FOUND);}
    private QualificationFileContent.Download open(long tenant,long id,long attachmentId,String action){
        var file=mapper.file(tenant,id,attachmentId);if(file==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(!fileReferences.active(file.fileId()))throw new ApiException(CommonErrorCode.NOT_FOUND);
        var opened=files.open(file.fileId());
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{opened.stream().close();}catch(IOException ignored){}}});
        try{audit.append(new AuditService.AuditCommand(action,"QUALIFICATION",id,null,Map.of(),Map.of("attachmentId",Long.toString(attachmentId)),null,null));return new QualificationFileContent.Download(file.fileName(),opened.size(),opened.stream());}
        catch(RuntimeException e){try{opened.stream().close();}catch(IOException close){e.addSuppressed(close);}throw e;}
    }
}
