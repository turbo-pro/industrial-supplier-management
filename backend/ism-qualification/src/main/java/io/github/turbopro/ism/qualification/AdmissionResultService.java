package io.github.turbopro.ism.qualification;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext;
import io.github.turbopro.ism.common.infrastructure.portal.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
@Service public class AdmissionResultService {
 private final AdmissionResultMapper mapper;private final AdmissionService admissions;private final AdmissionPortalMapper applications;
 private final PortalSupplierAccessGate suppliers;private final PortalModuleAccessGate modules;private final OperationIdGenerator ids;private final AuditService audit;
 public AdmissionResultService(AdmissionResultMapper mapper,AdmissionService admissions,AdmissionPortalMapper applications,PortalSupplierAccessGate suppliers,PortalModuleAccessGate modules,OperationIdGenerator ids,AuditService audit){this.mapper=mapper;this.admissions=admissions;this.applications=applications;this.suppliers=suppliers;this.modules=modules;this.ids=ids;this.audit=audit;}
 @Transactional(readOnly=true) public AdmissionResultModels.EmployeePage employeeList(long id){action("supplier:admission:view");var current=admissions.get(id);return new AdmissionResultModels.EmployeePage(AuthorizationContext.require().hasAction("supplier:admission:review")&&publishable(current.status().name(),current.version(),mapper.latestReview(tenant(),id))&&mapper.byVersion(tenant(),id,current.version())==null,AuthorizationContext.require().hasAction("supplier:admission:review"),mapper.employeeList(tenant(),id));}
 @Transactional(isolation=Isolation.READ_COMMITTED) public AdmissionResultModels.View publish(long id,AdmissionResultModels.Publish c){
  action("supplier:admission:view");action("supplier:admission:review");var allowed=admissions.get(id);suppliers.lockForResolution(Long.parseLong(allowed.supplierId()));var current=applications.application(tenant(),id);admissions.get(id);
  if(c.version()==null||c.version()<0||c.publicComment()==null||c.publicComment().isBlank()||c.publicComment().length()>1000)throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"请填写1000字以内的供应商可见说明和有效版本");
  String comment=c.publicComment().trim();var previous=mapper.byVersion(tenant(),id,c.version());
  if(previous!=null){if(!mapper.originalByVersion(tenant(),id,c.version()).equals(comment))throw conflict();return previous;}
  var review=mapper.latestReview(tenant(),id);
  if(current==null||current.version()!=c.version()||!publishable(current.status(),current.version(),review))throw conflict();
  long published=ids.nextId();try{mapper.publish(published,tenant(),current.supplierId(),id,review.id(),current.version(),current.applicationNo(),current.purchaseCategory(),current.status(),comment,TenantContext.require().actorId());}catch(org.springframework.dao.DuplicateKeyException duplicate){throw conflict();}
  audit.append(new AuditService.AuditCommand("ADMISSION_RESULT_PUBLISH","ADMISSION",id,null,Map.of(),Map.of("publicationId",Long.toString(published),"result",current.status(),"sourceVersion",current.version()),null,null));return mapper.byVersion(tenant(),id,c.version());
 }
 @Transactional(isolation=Isolation.READ_COMMITTED) public AdmissionResultModels.View correct(long id,long publicationId,AdmissionResultModels.Correct c){return change(id,publicationId,c.version(),"CORRECT",c.publicComment(),c.reason());}
 @Transactional(isolation=Isolation.READ_COMMITTED) public AdmissionResultModels.View withdraw(long id,long publicationId,AdmissionResultModels.Withdraw c){return change(id,publicationId,c.version(),"WITHDRAW",null,c.reason());}
 @Transactional(readOnly=true) public AdmissionResultModels.History history(long id,long publicationId,int page,int size){action("supplier:admission:view");admissions.get(id);page(page,size);String original=mapper.original(tenant(),id,publicationId);if(original==null)throw new ApiException(CommonErrorCode.NOT_FOUND);return new AdmissionResultModels.History(original,mapper.changeCount(tenant(),publicationId),page,size,mapper.changes(tenant(),publicationId,page*size,size));}
 private AdmissionResultModels.View change(long id,long publicationId,Integer version,String kind,String comment,String reason){
  action("supplier:admission:view");action("supplier:admission:review");var allowed=admissions.get(id);suppliers.lockForResolution(Long.parseLong(allowed.supplierId()));applications.application(tenant(),id);admissions.get(id);
  var source=mapper.lock(tenant(),publicationId);if(source==null||source.applicationId()!=id)throw new ApiException(CommonErrorCode.NOT_FOUND);
  if(version==null||version<0||version==Integer.MAX_VALUE||reason==null||reason.isBlank()||reason.length()>500||kind.equals("CORRECT")&&(comment==null||comment.isBlank()||comment.length()>1000))throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"请填写有效发布版本、对外原因及更正说明");
  reason=reason.trim();comment=comment==null?null:comment.trim();var previous=mapper.change(tenant(),publicationId,version+1);
  if(previous!=null){if(!previous.kind().equals(kind)||!Objects.equals(previous.publicComment(),comment)||!previous.publicReason().equals(reason))throw conflict();return mapper.find(tenant(),id,publicationId);}
  var current=mapper.find(tenant(),id,publicationId);if(source.changeVersion()!=version||current.publicationState().equals("WITHDRAWN"))throw conflict();
  if(mapper.advance(tenant(),publicationId,version)!=1)throw conflict();long event=ids.nextId();mapper.insertChange(event,tenant(),publicationId,version+1,kind,comment,reason,TenantContext.require().actorId());
  audit.append(new AuditService.AuditCommand("ADMISSION_RESULT_"+kind,"ADMISSION",id,null,Map.of("publicationVersion",version),Map.of("publicationId",Long.toString(publicationId),"changeId",Long.toString(event),"publicationVersion",version+1),null,null));return mapper.find(tenant(),id,publicationId);
 }
 private void page(int page,int size){if(page<0||page>10000||size<1||size>50)throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"分页参数无效");}
 @Transactional(readOnly=true) public AdmissionResultModels.Page portalList(int page,int size){var i=PortalIdentityContext.require();modules.requireAvailable("QUALIFICATION");page(page,size);return new AdmissionResultModels.Page(mapper.count(i.tenantId(),i.supplierId()),page,size,mapper.portalList(i.tenantId(),i.supplierId(),page*size,size));}
 private boolean publishable(String status,int version,AdmissionResultModels.Review review){if(review==null||review.applicationVersion()==null&&review.sameInstantCount()!=1||review.applicationVersion()!=null&&review.applicationVersion()!=version||!review.toStatus().equals(status))return false;return switch(status){case "APPROVED"->review.action().equals("APPROVE");case "REJECTED"->review.action().equals("REJECT");case "REVISION_REQUIRED"->review.action().equals("REQUIRE_REVISION");default->false;};}
 private void action(String permission){if(!AuthorizationContext.require().hasAction(permission))throw new ApiException(CommonErrorCode.FORBIDDEN);}
 private long tenant(){return TenantContext.require().tenantId();}
 private ApiException conflict(){return new ApiException(CommonErrorCode.CONFLICT,"该审核版本不可发布、申请已变化或已发布其他说明，请刷新核查");}
}
