package io.github.turbopro.ism.platform.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SupportRequestService {
 private static final String REQUEST="platform:support:request",VIEW="platform:support:view",REVIEW="platform:support:review";
 private static final SupportRequestModels.Profile PROFILE=new SupportRequestModels.Profile("SUPPLIER_BASIC_READ_V1","SUPPLIER_SUPPORT_READONLY_V1","TENANT_ALL",List.of("supplier:master:view"),List.of("id","code","name","type","status"));
 private final SupportRequestMapper mapper;private final OperationIdGenerator ids;private final AuditService audit;private final ObjectMapper json;
 public SupportRequestService(SupportRequestMapper mapper,OperationIdGenerator ids,AuditService audit,ObjectMapper json){this.mapper=mapper;this.ids=ids;this.audit=audit;this.json=json;}
 public SupportRequestModels.Page list(boolean review,int page,int size){require(VIEW);if(review)require(REVIEW);if(page<0||page>10000||size<1||size>100)throw new ApiException(CommonErrorCode.VALIDATION_FAILED);long actor=actor();var now=mapper.now();return new SupportRequestModels.Page(mapper.count(actor,review),page,size,mapper.list(actor,review,page*size,size).stream().map(r->view(r,now,false)).toList());}
 public SupportRequestModels.View get(long id){require(VIEW);var r=visible(id);return view(r,mapper.now(),true);}
 @Transactional public SupportRequestModels.View create(long tenant,SupportRequestModels.Create c){require(REQUEST);availableTenant(tenant);if(!PROFILE.code().equals(c.profileCode())||c.durationMinutes()==null||c.durationMinutes()<5||c.durationMinutes()>60)throw new ApiException(CommonErrorCode.VALIDATION_FAILED);var now=mapper.now();long id=ids.nextId();mapper.insert(id,"SUP-"+id,actor(),tenant,c.ticketNo().trim(),c.reason().trim(),write(PROFILE),now.plusMinutes(c.durationMinutes()));event(id,0,"CREATE","创建只读支持提案",now);return view(mapper.get(id),now,true);}
 @Transactional public SupportRequestModels.View change(long id,String action,SupportRequestModels.Change c){
  boolean review=Set.of("approve","reject").contains(action);require(review?REVIEW:REQUEST);if(review)require(VIEW);
  var before=mapper.get(id);if(before==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
  boolean beforeMine=before.platformOperatorId()==actor();
  if(!review&&!beforeMine||review&&(before.submittedAt()==null||beforeMine))throw new ApiException(review&&beforeMine?CommonErrorCode.FORBIDDEN:CommonErrorCode.NOT_FOUND);
  mapper.lockTenant(before.targetTenantId());
  // Keep tenant -> request lock ordering consistent with tenant suspension and future activation.
  if(Set.of("submit","approve").contains(action))availableTenant(before.targetTenantId());
  var r=mapper.lock(id);var now=mapper.now();boolean mine=r.platformOperatorId()==actor();
  if(!review&&!mine||review&&(r.submittedAt()==null||mine))throw new ApiException(review&&mine?CommonErrorCode.FORBIDDEN:CommonErrorCode.NOT_FOUND,"不能操作他人的申请或审批自己的申请");
  if(c.version()==null||c.version()!=r.version())throw new ApiException(CommonErrorCode.CONFLICT,"支持申请已变化，请重新读取");
  String next;
  switch(action){
   case "submit" -> {if(!"DRAFT".equals(r.status())||!r.requestedExpireAt().isAfter(now))throw blocked("草稿已失效或已提交");next="SUBMITTED";}
   case "approve" -> {if(!"SUBMITTED".equals(r.status())||!r.requestedExpireAt().isAfter(now)||!"ACTIVE".equals(r.operatorStatus()))throw blocked("申请已失效、已处理或申请账号不可用");next="PLATFORM_APPROVED";}
   case "reject" -> {if(!"SUBMITTED".equals(r.status()))throw blocked("只能拒绝已提交的待处理申请");next="REJECTED";}
   case "cancel" -> {if(!Set.of("DRAFT","SUBMITTED","PLATFORM_APPROVED").contains(r.status()))throw blocked("当前申请不能取消");next="CANCELLED";}
   default -> throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
  }
  if(mapper.update(id,r.version(),next,now)!=1)throw new ApiException(CommonErrorCode.CONFLICT);
  if(review)mapper.approve(ids.nextId(),id,r.platformOperatorId(),actor(),action.toUpperCase(Locale.ROOT),c.comment().trim(),now);
  event(id,r.version()+1,action.toUpperCase(Locale.ROOT),c.comment().trim(),now);
  return view(mapper.get(id),now,true);
 }
 private void availableTenant(long id){var t=mapper.lockTenant(id);if(t==null)throw new ApiException(CommonErrorCode.NOT_FOUND);if(!"ACTIVE".equals(t.status())||!"READY".equals(t.initializationStatus()))throw blocked("目标租户未开通或已暂停，不能申请/提交/批准支持");}
 private SupportRequestModels.Row visible(long id){var r=mapper.get(id);if(r==null||r.platformOperatorId()!=actor()&&(!has(REVIEW)||r.submittedAt()==null))throw new ApiException(CommonErrorCode.NOT_FOUND);return r;}
 private SupportRequestModels.View view(SupportRequestModels.Row r,LocalDateTime now,boolean detail){boolean mine=r.platformOperatorId()==actor(),alive=r.requestedExpireAt().isAfter(now);String status=!alive&&Set.of("DRAFT","SUBMITTED","PLATFORM_APPROVED").contains(r.status())?"EXPIRED":r.status();return new SupportRequestModels.View(Long.toString(r.id()),r.bizNo(),Long.toString(r.platformOperatorId()),r.operatorName(),Long.toString(r.targetTenantId()),r.tenantCode(),r.tenantName(),r.ticketNo(),r.reason(),read(r.profileJson()),r.requestedExpireAt(),status,r.submittedAt(),r.version(),r.createdAt(),mine&&has(REQUEST)&&alive&&"DRAFT".equals(r.status()),mine&&has(REQUEST)&&Set.of("DRAFT","SUBMITTED","PLATFORM_APPROVED").contains(r.status()),!mine&&has(REVIEW)&&alive&&"ACTIVE".equals(r.operatorStatus())&&"SUBMITTED".equals(r.status()),detail?mapper.events(r.id()):List.of());}
 private void event(long id,int version,String action,String comment,LocalDateTime now){mapper.event(ids.nextId(),id,actor(),version,action,comment,now);audit.append(new AuditService.AuditCommand("SUPPORT_REQUEST_"+action,"SUPPORT_REQUEST",id,null,Map.of(),Map.of("requestVersion",version,"action",action),null,null));}
 private long actor(){var i=TenantContext.require();if(i.tenantId()!=0)throw new ApiException(CommonErrorCode.FORBIDDEN);return i.actorId();}
 private boolean has(String p){return AuthorizationContext.require().hasAction(p);}
 private void require(String p){actor();if(!has(p))throw new ApiException(CommonErrorCode.FORBIDDEN);}
 private ApiException blocked(String m){return new ApiException(CommonErrorCode.RULE_BLOCKED,m);}
 private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
 private SupportRequestModels.Profile read(String v){try{return json.readValue(v,SupportRequestModels.Profile.class);}catch(Exception e){throw new IllegalStateException(e);}}
}
