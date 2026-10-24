package io.github.turbopro.ism.qualification;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.portal.*;
import io.github.turbopro.ism.operation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
public class PortalCertificateSubmissionService {
    private final PortalCertificateSubmissionMapper mapper;private final QualificationMapper qualifications;
    private final PortalWriteGuard guard;private final PortalModuleAccessGate modules;private final PortalQualificationUploadReference uploads;
    private final OperationIdGenerator ids;private final AuditService audit;private final ObjectMapper json;
    public PortalCertificateSubmissionService(PortalCertificateSubmissionMapper mapper,QualificationMapper qualifications,PortalWriteGuard guard,PortalModuleAccessGate modules,PortalQualificationUploadReference uploads,OperationIdGenerator ids,AuditService audit,ObjectMapper json){this.mapper=mapper;this.qualifications=qualifications;this.guard=guard;this.modules=modules;this.uploads=uploads;this.ids=ids;this.audit=audit;this.json=json;}
    @Transactional(readOnly=true) public List<PortalCertificateSubmissionModels.Type> types(){var i=identity();return qualifications.types(i.tenantId()).stream().filter(t->t.status().equals("ACTIVE")).map(t->new PortalCertificateSubmissionModels.Type(Long.toString(t.id()),t.typeCode(),t.typeName(),t.validityRequired())).toList();}
    @Transactional(readOnly=true) public PortalCertificateSubmissionModels.Page list(int page,int size){var i=identity();if(page<0||page>10000||size<1||size>100)throw invalid("分页参数无效");return new PortalCertificateSubmissionModels.Page(mapper.count(i.tenantId(),i.supplierId()),page,size,mapper.list(i.tenantId(),i.supplierId(),page*size,size));}
    @Transactional public PortalCertificateSubmissionModels.View submit(PortalCertificateSubmissionModels.Submit command){
        guard.lockCurrent();var i=identity();String request=write(command);var previous=mapper.byKey(i.tenantId(),i.supplierId(),command.requestKey());
        if(previous!=null){if(previous.accountId()!=i.accountId()||!same(previous.requestJson(),command))throw new ApiException(CommonErrorCode.CONFLICT,"提交标识已用于其他内容");return mapper.find(i.tenantId(),i.supplierId(),previous.qualificationId());}
        boolean renewal=command.renewalParentId()!=null;
        if(renewal!=(command.renewalParentVersion()!=null))throw invalid("换证必须同时提供原证照和版本");
        var type=qualifications.typeForReview(i.tenantId(),id(command.typeId()));
        if(type==null||!type.status().equals("ACTIVE"))throw invalid("资质类型不可用，请刷新后选择");
        PortalCertificateSubmissionModels.Parent parent=null;
        if(renewal){
            parent=qualifications.lockRenewalParent(i.tenantId(),i.supplierId(),id(command.renewalParentId()));
            if(parent==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
            if(!parent.status().equals("VALID")||parent.version()!=command.renewalParentVersion())throw new ApiException(CommonErrorCode.CONFLICT,"原证照已变化，请刷新后申请");
            if(parent.typeId()!=type.id())throw invalid("换证不能改变资质类型");
            if(command.effectiveDate()!=null&&command.effectiveDate().isAfter(LocalDate.now())||command.expiryDate()!=null&&command.expiryDate().isBefore(LocalDate.now()))throw invalid("换证新证必须已生效且未过期，暂不支持计划生效");
        }
        Long verified=qualifications.currentVerifiedCertificate(i.tenantId(),type.id(),command.certificateNo());
        if(verified!=null&&(parent==null||verified!=parent.id()))throw new ApiException(CommonErrorCode.CONFLICT,"该编号已有其他核验证照，请核查后申请");
        if(command.permanent()&&command.expiryDate()!=null||!command.permanent()&&type.validityRequired()&&command.expiryDate()==null)throw invalid("请按资质类型填写有效期");
        if(command.issueDate()!=null&&command.issueDate().isAfter(LocalDate.now())||command.effectiveDate()!=null&&command.expiryDate()!=null&&command.effectiveDate().isAfter(command.expiryDate()))throw invalid("签发或有效期日期无效");
        var file=uploads.requireReady(id(command.uploadId()));long qualificationId=ids.nextId();
        Long organization=qualifications.supplierOrganization(i.tenantId(),i.supplierId());if(organization==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        try{
            qualifications.insert(qualificationId,i.tenantId(),i.accountId(),organization,i.supplierId(),type.id(),parent==null?null:parent.id(),command.certificateNo(),command.issuingAuthority(),command.issueDate(),command.effectiveDate(),command.permanent()?null:command.expiryDate(),command.permanent(),type.defaultWarningDays(),file.fileId());
            mapper.insert(qualificationId,i.tenantId(),i.supplierId(),i.accountId(),command.requestKey(),id(command.uploadId()),request,file.fileName());
            if(parent!=null)mapper.insertRenewal(qualificationId,i.tenantId(),i.supplierId(),parent.id(),parent.version());
        }catch(org.springframework.dao.DuplicateKeyException e){throw new ApiException(CommonErrorCode.CONFLICT,"证书编号重复或原件已用于其他申请");}
        audit.append(new AuditService.AuditCommand(renewal?"PORTAL_CERTIFICATE_RENEWAL_SUBMIT":"PORTAL_CERTIFICATE_SUBMIT","QUALIFICATION",qualificationId,null,Map.of(),Map.of("supplierId",i.supplierId()),null,null));
        return mapper.find(i.tenantId(),i.supplierId(),qualificationId);
    }
    private PortalIdentityContext.Identity identity(){var i=PortalIdentityContext.require();modules.requireAvailable("QUALIFICATION");return i;}
    private long id(String value){try{return Long.parseLong(value);}catch(NumberFormatException e){throw invalid("标识无效");}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private boolean same(String value,PortalCertificateSubmissionModels.Submit command){try{return json.readValue(value,PortalCertificateSubmissionModels.Submit.class).equals(command);}catch(Exception e){throw new IllegalStateException(e);}}
    private ApiException invalid(String text){return new ApiException(CommonErrorCode.VALIDATION_FAILED,text);}
}
