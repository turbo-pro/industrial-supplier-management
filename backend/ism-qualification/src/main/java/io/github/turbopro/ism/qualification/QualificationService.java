package io.github.turbopro.ism.qualification;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
public class QualificationService {
    private final QualificationMapper mapper;private final OperationIdGenerator ids;private final AuditService audit;private final QualificationFileReference files;
    private final io.github.turbopro.ism.common.infrastructure.portal.PortalSupplierAccessGate portalSuppliers;
    public QualificationService(QualificationMapper mapper,OperationIdGenerator ids,AuditService audit,QualificationFileReference files,io.github.turbopro.ism.common.infrastructure.portal.PortalSupplierAccessGate portalSuppliers){this.mapper=mapper;this.ids=ids;this.audit=audit;this.files=files;this.portalSuppliers=portalSuppliers;}
    public List<QualificationModels.TypeView> types(){return mapper.types(TenantContext.require().tenantId()).stream().map(this::typeView).toList();}
    @Transactional public QualificationModels.TypeView createType(QualificationModels.SaveType c){if(c.version()!=0)throw validation("新建资质类型的 version 必须为 0");var i=TenantContext.require();long id=ids.nextId();try{mapper.insertType(id,i.tenantId(),i.actorId(),c.code(),c.name().trim(),c.category(),c.validityRequired(),c.defaultWarningDays(),blank(c.description()),c.status().name());}catch(DuplicateKeyException e){throw new ApiException(CommonErrorCode.CONFLICT,"资质类型编码已存在");}return typeView(mapper.type(i.tenantId(),id));}
    @Transactional public QualificationModels.TypeView updateType(long id,QualificationModels.SaveType c){var i=TenantContext.require();var before=mapper.type(i.tenantId(),id);if(before==null)throw new ApiException(CommonErrorCode.NOT_FOUND);if(!before.typeCode().equals(c.code()))throw validation("资质类型编码不可修改");if(mapper.updateType(i.tenantId(),i.actorId(),id,c.name().trim(),c.category(),c.validityRequired(),c.defaultWarningDays(),blank(c.description()),c.status().name(),c.version())!=1)throw new ApiException(CommonErrorCode.CONFLICT);return typeView(mapper.type(i.tenantId(),id));}
    public QualificationModels.Page list(String keyword,String status,Long supplierId,int page,int size){var i=TenantContext.require();var scope=scope();String like=keyword==null||keyword.isBlank()?null:"%"+keyword.trim().replace("=","==").replace("%","=%").replace("_","=_")+"%";String state=status==null||status.isBlank()?null:parseStatus(status).name();return new QualificationModels.Page(mapper.count(i.tenantId(),like,state,supplierId,scope.type().name(),scope.organizationIds(),i.actorId()),page,size,mapper.list(i.tenantId(),like,state,supplierId,scope.type().name(),scope.organizationIds(),i.actorId(),Math.multiplyExact(page,size),size));}
    public QualificationModels.View get(long id){return view(requireAllowed(id));}
    @Transactional public QualificationModels.View create(QualificationModels.SaveQualification c){if(c.version()!=0)throw validation("新建资质的 version 必须为 0");return createInternal(c,null);}
    @Transactional public QualificationModels.View update(long id,QualificationModels.SaveQualification c){var before=requireAllowed(id);if(!before.status().equals("DRAFT"))throw validation("只有待核验的证照可以编辑");if(before.supplierId()!=parseId(c.supplierId(),"供应商 ID 无效")||before.qualificationTypeId()!=parseId(c.typeId(),"资质类型 ID 无效"))throw validation("供应商和资质类型不可修改");validate(c,mapper.type(TenantContext.require().tenantId(),before.qualificationTypeId()));long fileId=requireFile(c.fileId());var i=TenantContext.require();try{if(mapper.update(i.tenantId(),i.actorId(),id,c.certificateNo().trim(),blank(c.issuingAuthority()),c.issueDate(),c.effectiveDate(),c.permanent()?null:c.expiryDate(),c.permanent(),warning(c,before.qualificationTypeId()),fileId,c.version())!=1)throw new ApiException(CommonErrorCode.CONFLICT);}catch(DuplicateKeyException e){throw new ApiException(CommonErrorCode.CONFLICT,"证书编号已存在");}audit.append(new AuditService.AuditCommand("QUALIFICATION_UPDATE","QUALIFICATION",id,null,Map.of("version",before.version()),Map.of("version",before.version()+1,"fileId",c.fileId()),null,null));return view(mapper.find(i.tenantId(),id));}
    @Transactional public QualificationModels.View verify(long id,QualificationModels.VerifyCommand c){
        var before=requireAllowed(id);var i=TenantContext.require();String next=c.decision()==QualificationModels.Decision.APPROVE?"VALID":"REJECTED";
        boolean portal=mapper.portalSubmission(i.tenantId(),id)>0;
        String publicComment=blank(c.publicComment());
        if(publicComment!=null&&publicComment.length()>1000)throw validation("对外审核说明不能超过1000字");
        if(!portal&&publicComment!=null)throw validation("只有门户提交的申请可以发布对外审核说明");
        if(portal&&c.decision()==QualificationModels.Decision.REJECT&&publicComment==null)throw validation("拒绝门户申请必须填写供应商可见的审核说明");
        if(portal)portalSuppliers.lockForResolution(before.supplierId());
        var renewal=portal?mapper.lockRenewal(i.tenantId(),id):null;
        if(renewal!=null&&!renewal.state().equals("PENDING"))throw new ApiException(CommonErrorCode.CONFLICT,"换证申请已处理");
        if(c.decision()==QualificationModels.Decision.APPROVE){
            if(!files.active(before.fileId()))throw validation("证照文件不存在或不可用");
            if(portal){
                try{portalSuppliers.lockAvailable(before.supplierId());}catch(ApiException unavailable){throw new ApiException(CommonErrorCode.RULE_BLOCKED,"企业不可用或正在退出，不能批准门户证照");}
                var type=mapper.typeForReview(i.tenantId(),before.qualificationTypeId());
                if(type==null||!type.status().equals("ACTIVE")||type.validityRequired()&&!before.permanent()&&before.expiryDate()==null)throw validation("资质类型或有效期规则已变更，请拒绝后重新提交");
            }
            if(renewal!=null){
                var parent=mapper.lockRenewalParent(i.tenantId(),before.supplierId(),renewal.parentId());
                if(parent==null||parent.typeId()!=before.qualificationTypeId()||before.parentQualificationId()==null||before.parentQualificationId()!=parent.id()||!parent.status().equals("VALID")||parent.version()!=renewal.parentVersion())throw new ApiException(CommonErrorCode.CONFLICT,"原证照已变更，请拒绝后重新申请");
                if(!scope().allows(new DataTarget(parent.organizationId(),null,parent.createdBy(),parent.createdBy()),i.actorId()))throw new ApiException(CommonErrorCode.NOT_FOUND);
                if(before.effectiveDate()!=null&&before.effectiveDate().isAfter(LocalDate.now())||before.expiryDate()!=null&&before.expiryDate().isBefore(LocalDate.now()))throw validation("换证新证尚未生效或已过期，不能替代原证");
                if(mapper.supersede(i.tenantId(),i.actorId(),parent.id(),parent.version())!=1)throw new ApiException(CommonErrorCode.CONFLICT,"原证照已变更");
            }
        }
        try{if(mapper.verify(i.tenantId(),i.actorId(),id,next,c.comment().trim(),c.version())!=1)throw new ApiException(CommonErrorCode.CONFLICT);}catch(DuplicateKeyException duplicate){throw new ApiException(CommonErrorCode.CONFLICT,"相同类型和编号已有核验证照，请刷新后核查");}
        if(portal){
            if(mapper.publishReview(i.tenantId(),id,publicComment)!=1)throw new ApiException(CommonErrorCode.CONFLICT,"审核反馈已发布，请刷新核查");
            audit.append(new AuditService.AuditCommand("PORTAL_CERTIFICATE_REVIEW_PUBLISH","QUALIFICATION",id,null,Map.of(),Map.of("decision",c.decision().name(),"hasPublicComment",publicComment!=null),null,null));
        }
        if(renewal!=null){
            if(mapper.finishRenewal(i.tenantId(),id,c.decision()==QualificationModels.Decision.APPROVE?"APPROVED":"REJECTED")!=1)throw new ApiException(CommonErrorCode.CONFLICT,"换证申请已处理");
            audit("PORTAL_CERTIFICATE_RENEWAL_"+c.decision(),id,"DRAFT",next,"原证照 "+renewal.parentId());
        }
        audit("QUALIFICATION_VERIFY",id,before.status(),next,c.comment());return view(mapper.find(i.tenantId(),id));
    }
    @Transactional public QualificationModels.View revoke(long id,QualificationModels.RevokeCommand c){var before=requireAllowed(id);var i=TenantContext.require();if(mapper.revoke(i.tenantId(),i.actorId(),id,c.reason().trim(),c.version())!=1)throw validation("只有已生效资质可以撤销，或数据版本已变化");audit("QUALIFICATION_REVOKE",id,before.status(),"REVOKED",c.reason());return view(mapper.find(i.tenantId(),id));}
    @Transactional public QualificationModels.View renew(long id,QualificationModels.SaveQualification c){var before=requireAllowed(id);if(!Set.of("VALID").contains(before.status()))throw validation("只有已生效资质可以换证");if(before.supplierId()!=parseId(c.supplierId(),"供应商 ID 无效")||before.qualificationTypeId()!=parseId(c.typeId(),"资质类型 ID 无效"))throw validation("换证不得变更供应商和资质类型");var replacement=createInternal(c,id);var i=TenantContext.require();if(mapper.supersede(i.tenantId(),i.actorId(),id,c.version())!=1)throw new ApiException(CommonErrorCode.CONFLICT);audit("QUALIFICATION_RENEW",id,before.status(),"SUPERSEDED","换证草稿 "+replacement.id());return replacement;}
    private QualificationModels.View createInternal(QualificationModels.SaveQualification c,Long parent){var i=TenantContext.require();long supplier=parseId(c.supplierId(),"供应商 ID 无效"),typeId=parseId(c.typeId(),"资质类型 ID 无效");Long org=mapper.supplierOrganization(i.tenantId(),supplier);if(org==null)throw validation("供应商不存在或已退出");requireScope(org,i.actorId());var type=mapper.type(i.tenantId(),typeId);validate(c,type);long fileId=requireFile(c.fileId());long id=ids.nextId();try{mapper.insert(id,i.tenantId(),i.actorId(),org,supplier,typeId,parent,c.certificateNo().trim(),blank(c.issuingAuthority()),c.issueDate(),c.effectiveDate(),c.permanent()?null:c.expiryDate(),c.permanent(),c.warningDays()==null?type.defaultWarningDays():c.warningDays(),fileId);}catch(DuplicateKeyException e){throw new ApiException(CommonErrorCode.CONFLICT,"证书编号已存在");}audit("QUALIFICATION_CREATE",id,null,"DRAFT","创建资质草稿");return view(mapper.find(i.tenantId(),id));}
    private long requireFile(String value){long id=parseId(value,"文件 ID 无效");if(!files.active(id))throw validation("证照文件不存在或不可用");return id;}
    private void validate(QualificationModels.SaveQualification c,QualificationModels.TypeRow type){if(type==null||!type.status().equals("ACTIVE"))throw validation("资质类型不存在或已停用");if(c.permanent()&&c.expiryDate()!=null)throw validation("长期有效资质不能填写到期日");if(!c.permanent()&&type.validityRequired()&&c.expiryDate()==null)throw validation("该资质必须填写到期日");if(c.effectiveDate()!=null&&c.expiryDate()!=null&&c.effectiveDate().isAfter(c.expiryDate()))throw validation("生效日期不能晚于到期日期");if(c.issueDate()!=null&&c.issueDate().isAfter(LocalDate.now()))throw validation("签发日期不能晚于今天");}
    private QualificationModels.Row requireAllowed(long id){var i=TenantContext.require();var row=mapper.find(i.tenantId(),id);if(row==null||!scope().allows(new DataTarget(row.organizationId(),null,row.createdBy(),row.createdBy()),i.actorId()))throw new ApiException(CommonErrorCode.NOT_FOUND);return row;}
    private DataScope scope(){return AuthorizationContext.require().dataScope("supplier:qualification");}
    private void requireScope(long org,long actor){if(!scope().allows(new DataTarget(org,null,actor,actor),actor))throw new ApiException(CommonErrorCode.FORBIDDEN);}
    private int warning(QualificationModels.SaveQualification c,long typeId){var type=mapper.type(TenantContext.require().tenantId(),typeId);return c.warningDays()==null?type.defaultWarningDays():c.warningDays();}
    private QualificationModels.Status parseStatus(String value){try{return QualificationModels.Status.valueOf(value);}catch(Exception e){throw validation("资质状态无效");}}
    private long parseId(String value,String message){try{return Long.parseLong(value);}catch(Exception e){throw validation(message);}}
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    private ApiException validation(String message){return new ApiException(CommonErrorCode.VALIDATION_FAILED,message);}
    private void audit(String action,long id,String from,String to,String comment){audit.append(new AuditService.AuditCommand(action,"QUALIFICATION",id,null,from==null?Map.of():Map.of("status",from),Map.of("status",to,"comment",comment),null,null));}
    private QualificationModels.TypeView typeView(QualificationModels.TypeRow r){return new QualificationModels.TypeView(Long.toString(r.id()),r.typeCode(),r.typeName(),r.category(),r.validityRequired(),r.defaultWarningDays(),r.description(),QualificationModels.TypeStatus.valueOf(r.status()),r.version(),r.updatedAt());}
    private QualificationModels.View view(QualificationModels.Row r){var correction=mapper.correctionInfo(TenantContext.require().tenantId(),r.id());return new QualificationModels.View(Long.toString(r.id()),Long.toString(r.organizationId()),Long.toString(r.supplierId()),r.supplierCode(),r.supplierName(),Long.toString(r.qualificationTypeId()),r.typeCode(),r.typeName(),r.parentQualificationId()==null?null:Long.toString(r.parentQualificationId()),r.certificateNo(),r.issuingAuthority(),r.issueDate(),r.effectiveDate(),r.expiryDate(),r.permanent(),r.warningDays(),Long.toString(r.fileId()),effective(r),r.verificationComment(),r.verifiedBy()==null?null:Long.toString(r.verifiedBy()),r.verifiedAt(),r.revokedReason(),r.version(),r.createdAt(),r.updatedAt(),mapper.portalSubmission(TenantContext.require().tenantId(),r.id())>0,mapper.publicReviewComment(TenantContext.require().tenantId(),r.id()),correction==null?null:correction.correctionOfId(),correction==null?0:correction.correctionRound(),correction==null?null:correction.correctionNote());}
    private QualificationModels.Status effective(QualificationModels.Row r){if(!r.status().equals("VALID")||r.permanent()||r.expiryDate()==null)return QualificationModels.Status.valueOf(r.status());long days=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),r.expiryDate());return days<0?QualificationModels.Status.EXPIRED:days<=r.warningDays()?QualificationModels.Status.EXPIRING:QualificationModels.Status.VALID;}
}
