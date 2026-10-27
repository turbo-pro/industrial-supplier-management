package io.github.turbopro.ism.qualification;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext;
import io.github.turbopro.ism.common.infrastructure.portal.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.*;
import java.io.IOException;
import java.util.*;

@Service public class AdmissionPortalService {
 private final AdmissionPortalMapper mapper;private final AdmissionService admissions;private final PortalWriteGuard guard;private final PortalModuleAccessGate modules;
 private final PortalQualificationUploadReference uploads;private final QualificationFileContent files;private final QualificationFileReference references;
 private final PortalSupplierAccessGate suppliers;private final OperationIdGenerator ids;private final AuditService audit;private final ObjectMapper json;
 public AdmissionPortalService(AdmissionPortalMapper mapper,AdmissionService admissions,PortalWriteGuard guard,PortalModuleAccessGate modules,PortalQualificationUploadReference uploads,QualificationFileContent files,QualificationFileReference references,OperationIdGenerator ids,AuditService audit,ObjectMapper json,PortalSupplierAccessGate suppliers){this.suppliers=suppliers;this.mapper=mapper;this.admissions=admissions;this.guard=guard;this.modules=modules;this.uploads=uploads;this.files=files;this.references=references;this.ids=ids;this.audit=audit;this.json=json;}
 @Transactional(readOnly=true) public AdmissionPortalModels.EmployeePage employeeList(long application){employee(application,false);var a=AuthorizationContext.require();return new AdmissionPortalModels.EmployeePage(a.hasAction("supplier:admission:apply"),a.hasAction("resource:file:download"),mapper.employeeList(tenant(),application).stream().map(this::view).toList());}
 @Transactional(isolation=Isolation.READ_COMMITTED) public AdmissionPortalModels.View publish(long application,AdmissionPortalModels.Publish c){
  employee(application,true);suppliers.lockForResolution(Long.parseLong(admissions.get(application).supplierId()));mapper.lockApplication(tenant(),application);var current=admissions.get(application);editable(current);
  if(current.version()!=c.version())throw conflict();
  if(c.instruction()==null||c.instruction().isBlank()||c.instruction().length()>1000||c.types()==null||c.types().isEmpty()||c.types().size()>5||new HashSet<>(c.types()).size()!=c.types().size())throw invalid("请选择1至5项材料，并填写对外说明");
  var selected=current.materials().stream().filter(m->c.types().contains(m.type())).toList();if(selected.size()!=c.types().size())throw invalid("材料不在当前申请中");
  // No internal file, remark, amount, reason or review is copied to the public snapshot.
  Long active=mapper.active(tenant(),application);if(active!=null)throw conflict();
  long id=ids.nextId();mapper.publish(id,tenant(),Long.parseLong(current.supplierId()),application,current.version(),current.applicationNo(),c.instruction().trim(),TenantContext.require().actorId());
  for(int n=0;n<selected.size();n++){var item=selected.get(n);mapper.insertItem(tenant(),id,item.type(),item.name(),n);}
  record("ADMISSION_PORTAL_PUBLISH",id,Map.of("applicationId",Long.toString(application),"materialCount",selected.size()));return view(mapper.find(tenant(),id));
 }
 @Transactional(readOnly=true) public AdmissionPortalModels.Page portalList(int page,int size){var i=portal();if(page<0||page>10000||size<1||size>50)throw invalid("分页无效");return new AdmissionPortalModels.Page(mapper.count(i.tenantId(),i.supplierId()),page,size,mapper.portalList(i.tenantId(),i.supplierId(),page*size,size).stream().map(this::view).toList());}
 @Transactional(isolation=Isolation.READ_COMMITTED) public AdmissionPortalModels.View submit(long id,AdmissionPortalModels.Submit c){
  guard.lockCurrent();var i=portal();var row=own(id,i.supplierId());
  mapper.lockApplication(tenant(),row.applicationId());row=mapper.lock(tenant(),id);
  if(c.materials()==null||c.materials().isEmpty()||c.materials().size()>5)throw invalid("请提交索取清单的全部材料");
  String command=write(new AdmissionPortalModels.Submit(c.version(),c.materials().stream().sorted(Comparator.comparing(AdmissionPortalModels.Material::type)).toList()));
  if(row.submissionJson()!=null){if(!Objects.equals(row.submittedBy(),i.accountId())||!same(row.submissionJson(),command))throw conflict();return view(row);}
  if(!row.state().equals("OPEN")||row.version()!=c.version())throw conflict();
  // Check current application without giving the portal internal employee data privileges.
  var application=currentApplication(row);if(!Set.of("DRAFT","REVISION_REQUIRED").contains(application.status())||application.version()!=row.applicationVersion())throw conflict();
  var expected=mapper.items(tenant(),id);var types=new HashSet<String>();var used=new HashSet<String>();
  for(var material:c.materials())if(!types.add(material.type())||!used.add(material.uploadId()))throw invalid("材料类型或上传不可重复");
  if(!types.equals(new HashSet<>(expected.stream().map(AdmissionPortalModels.ItemRow::materialType).toList())))throw invalid("须逐项提供本次索取的全部材料");
  try{for(var material:c.materials()){long upload=uploadId(material.uploadId());var ready=uploads.requireReady(upload);mapper.consume(upload,tenant(),i.supplierId(),id);if(mapper.attach(tenant(),id,material.type(),upload,ready.fileId(),ready.fileName())!=1)throw conflict();}}
  catch(org.springframework.dao.DuplicateKeyException duplicate){throw new ApiException(CommonErrorCode.CONFLICT,"上传已被其他申请使用，请重新上传");}
  if(mapper.submit(tenant(),id,i.accountId(),command,c.version())!=1)throw conflict();record("ADMISSION_PORTAL_SUBMIT",id,Map.of("materialCount",types.size()));return view(mapper.find(tenant(),id));
 }
 @Transactional(isolation=Isolation.READ_COMMITTED) public AdmissionPortalModels.View accept(long id,AdmissionPortalModels.Version c){
  var before=required(id);employee(before.applicationId(),true);suppliers.lockForResolution(before.supplierId());mapper.lockApplication(tenant(),before.applicationId());var row=mapper.lock(tenant(),id);
  if(!row.state().equals("SUBMITTED")||row.version()!=c.version())throw conflict();var current=admissions.get(row.applicationId());editable(current);if(current.version()!=row.applicationVersion())throw conflict();
  var incoming=mapper.items(tenant(),id);var byType=new HashMap<String,AdmissionPortalModels.ItemRow>();for(var item:incoming){if(item.fileId()==null||!references.active(item.fileId()))throw invalid("供应商提交的文件已不可用");byType.put(item.materialType(),item);}
  if(!current.materials().stream().map(AdmissionModels.MaterialView::type).toList().containsAll(byType.keySet()))throw conflict();
  var materials=current.materials().stream().map(m->{var item=byType.get(m.type());return new AdmissionModels.MaterialCommand(m.type(),m.name(),item==null?m.fileId():Long.toString(item.fileId()),m.required(),item!=null||m.provided(),m.remark(),m.sortOrder());}).toList();
  admissions.update(row.applicationId(),new AdmissionModels.SaveApplication(current.supplierId(),current.purchaseCategory(),current.reason(),current.expectedAnnualAmount(),current.currency(),materials,current.version(),current.purchaseCategoryId()));
  if(mapper.close(tenant(),id,"ACCEPTED",null,c.version())!=1)throw conflict();record("ADMISSION_PORTAL_ACCEPT",id,Map.of("applicationId",Long.toString(row.applicationId())));return view(mapper.find(tenant(),id));
 }
 @Transactional(isolation=Isolation.READ_COMMITTED) public AdmissionPortalModels.View cancel(long id,AdmissionPortalModels.Cancel c){var before=required(id);employee(before.applicationId(),true);suppliers.lockForResolution(before.supplierId());mapper.lockApplication(tenant(),before.applicationId());var row=mapper.lock(tenant(),id);if(c.reason()==null||c.reason().isBlank()||c.reason().length()>500)throw invalid("请填写500字以内的供应商可见撤销说明");if(mapper.close(tenant(),id,"CANCELLED",c.reason().trim(),c.version())!=1)throw conflict();record("ADMISSION_PORTAL_CANCEL",id,Map.of("reason",c.reason().trim()));return view(mapper.find(tenant(),id));}
 @Transactional(isolation=Isolation.READ_COMMITTED) public QualificationFileContent.Download portalDownload(long id,String type){guard.lockCurrent();var i=portal();var row=own(id,i.supplierId());return download(row,type,"ADMISSION_PORTAL_FILE_OPEN");}
 @Transactional(isolation=Isolation.READ_COMMITTED) public QualificationFileContent.Download employeeDownload(long id,String type){var row=required(id);employee(row.applicationId(),false);action("resource:file:download");return download(row,type,"ADMISSION_EMPLOYEE_FILE_OPEN");}
 private QualificationFileContent.Download download(AdmissionPortalModels.Row row,String type,String action){var item=mapper.items(tenant(),row.id()).stream().filter(m->m.materialType().equals(type)).findFirst().orElse(null);if(item==null||item.fileId()==null||!references.active(item.fileId()))throw new ApiException(CommonErrorCode.NOT_FOUND);var opened=files.open(item.fileId());if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{opened.stream().close();}catch(IOException ignored){}}});try{record(action,row.id(),Map.of("materialType",type));return new QualificationFileContent.Download(item.fileName(),opened.size(),opened.stream());}catch(RuntimeException e){try{opened.stream().close();}catch(IOException close){e.addSuppressed(close);}throw e;}}
 private void employee(long application,boolean write){action("supplier:admission:view");if(write)action("supplier:admission:apply");admissions.get(application);}
 private void action(String action){if(!AuthorizationContext.require().hasAction(action))throw new ApiException(CommonErrorCode.FORBIDDEN);}
 private void editable(AdmissionModels.View current){if(!Set.of(AdmissionModels.Status.DRAFT,AdmissionModels.Status.REVISION_REQUIRED).contains(current.status()))throw conflict();}
 private AdmissionPortalModels.Row own(long id,long supplier){var row=required(id);if(row.supplierId()!=supplier)throw new ApiException(CommonErrorCode.NOT_FOUND);return row;}
 private AdmissionPortalModels.Row required(long id){var row=mapper.find(tenant(),id);if(row==null)throw new ApiException(CommonErrorCode.NOT_FOUND);return row;}
 private AdmissionModels.Row currentApplication(AdmissionPortalModels.Row row){return mapper.application(tenant(),row.applicationId());}
 private PortalIdentityContext.Identity portal(){var i=PortalIdentityContext.require();modules.requireAvailable("QUALIFICATION");return i;}
 private AdmissionPortalModels.View view(AdmissionPortalModels.Row row){return new AdmissionPortalModels.View(Long.toString(row.id()),row.applicationNo(),row.instruction(),row.state(),row.version(),row.closeNote(),row.createdAt(),row.submittedAt(),mapper.items(tenant(),row.id()).stream().map(m->new AdmissionPortalModels.Item(m.materialType(),m.materialName(),m.fileName())).toList());}
 private long uploadId(String value){try{return Long.parseLong(value);}catch(NumberFormatException e){throw invalid("上传标识无效");}}
 private long tenant(){return TenantContext.require().tenantId();}
 private boolean same(String a,String b){try{return json.readTree(a).equals(json.readTree(b));}catch(Exception e){throw new IllegalStateException(e);}}
 private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 private void record(String action,long id,Map<String,Object> after){audit.append(new AuditService.AuditCommand(action,"ADMISSION_MATERIAL_REQUEST",id,null,Map.of(),after,null,null));}
 private ApiException conflict(){return new ApiException(CommonErrorCode.CONFLICT,"材料索取或准入申请已变化，请刷新；如申请已修改，请撤销旧索取后重新发布");}
 private ApiException invalid(String text){return new ApiException(CommonErrorCode.VALIDATION_FAILED,text);}
}
