package io.github.turbopro.ism.platform.packageplan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.operation.AuditService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Service
public class PackagePlanService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PackagePlanMapper mapper; private final ObjectMapper json;
    private final Map<String,io.github.turbopro.ism.common.api.quota.TenantQuotaUsageReader> usageReaders;
    private final AuditService audit;
    private final io.github.turbopro.ism.common.api.authorization.ModuleCapabilityCatalog capabilityCatalog;
    public PackagePlanService(PackagePlanMapper mapper, ObjectMapper json,AuditService audit,List<io.github.turbopro.ism.common.api.quota.TenantQuotaUsageReader> readers,io.github.turbopro.ism.common.api.authorization.ModuleCapabilityCatalog capabilityCatalog) {this.capabilityCatalog=capabilityCatalog; this.mapper=mapper; this.json=json; this.audit=audit;this.usageReaders=readers.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(io.github.turbopro.ism.common.api.quota.TenantQuotaUsageReader::quotaCode,r->r)); }

    @Transactional(readOnly=true)
    public PackagePlanModels.SubscriptionPage subscriptions(long tenantId,int page,int size) {
        if(page<0 || page>10000 || size<1 || size>100) throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        var tenant=mapper.subscriptionTenant(tenantId);
        if(tenant==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        var now=mapper.utcNow(); var latest=mapper.latestStartedSubscription(tenantId,now);
        var current=mapper.findTenantPackageVersion(tenantId);
        return new PackagePlanModels.SubscriptionPage(Long.toString(tenantId),tenant.version(),
                current==null?null:current.toString(),now,mapper.subscriptionCount(tenantId),page,size,
                mapper.subscriptions(tenantId,Math.multiplyExact(page,size),size).stream().map(row->{
                    String state;
                    if("CANCELLED".equals(row.status())) state="CANCELLED";
                    else if(!"ACTIVE".equals(row.status())) state="SUPERSEDED";
                    else if(row.effectiveFrom().isAfter(now)) state="SCHEDULED";
                    else if(!Objects.equals(latest,row.id())) state="SUPERSEDED";
                    else if(row.effectiveTo()!=null && !row.effectiveTo().isAfter(now)) state="EXPIRED";
                    else if(!"PUBLISHED".equals(row.versionStatus())) state="UNAVAILABLE";
                    else state="EFFECTIVE";
                    return new PackagePlanModels.SubscriptionView(Long.toString(row.id()),Long.toString(row.packageVersionId()),
                            row.packageName(),row.versionName(),state,row.effectiveFrom(),row.effectiveTo(),"SCHEDULED".equals(state),
                            row.cancelledAt(),row.cancelledBy()==null?null:row.cancelledBy().toString(),row.cancelReason());
                }).toList());
    }

    @Transactional
    public PackagePlanModels.SubscriptionCancellation cancelSubscription(long tenantId,long subscriptionId,
            PackagePlanModels.CancelSubscription command,long actorId) {
        if(command.reason()==null || command.reason().isBlank() || command.reason().length()>500)
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        var tenant=mapper.lockSubscriptionTenant(tenantId);
        if(tenant==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        var row=mapper.subscription(tenantId,subscriptionId);
        if(row==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(tenant.version()!=command.tenantVersion()) throw new ApiException(CommonErrorCode.CONFLICT,"租户订阅已变化，请刷新后重试");
        var now=mapper.utcNow();
        if(mapper.cancelSubscription(tenantId,subscriptionId,now,actorId,command.reason().trim())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT,"只能取消尚未生效的计划");
        if(mapper.advanceSubscriptionTenantVersion(tenantId,tenant.version())!=1) throw new ApiException(CommonErrorCode.CONFLICT);
        audit.append(new AuditService.AuditCommand("SUBSCRIPTION_CANCEL","SUBSCRIPTION",subscriptionId,null,
                Map.of("status",row.status(),"tenantId",Long.toString(tenantId)),
                Map.of("status","CANCELLED","reason",command.reason().trim()),null,null));
        return new PackagePlanModels.SubscriptionCancellation(Long.toString(subscriptionId),tenant.version()+1,"CANCELLED");
    }

    public List<PackagePlanModels.ModuleOption> moduleOptions(){return mapper.moduleOptions();}
    public List<PackagePlanModels.PackageView> list() { return mapper.listPackages().stream().map(this::view).toList(); }
    public PackagePlanModels.PackageView get(long id) {
        PackagePlanModels.PackageRow row=mapper.findPackage(id); if(row==null) throw new ApiException(CommonErrorCode.NOT_FOUND); return view(row);
    }
    @Transactional
    public PackagePlanModels.PackageView create(PackagePlanModels.CreatePackage command) {
        long id=id(); try { mapper.insertPackage(id,command.code(),command.name()); }
        catch(DuplicateKeyException e){ throw new ApiException(PackagePlanErrorCode.DUPLICATE); }
        return get(id);
    }
    @Transactional
    public PackagePlanModels.PackageView update(long id, PackagePlanModels.UpdatePackage command) {
        if(mapper.updatePackage(id,command.name(),command.version())!=1) throw new ApiException(CommonErrorCode.CONFLICT,"套餐不是可编辑草稿或版本已变化"); return get(id);
    }
    @Transactional
    public PackagePlanModels.VersionView createVersion(long packageId, PackagePlanModels.CreateVersion command) {
        requireEditableParent(packageId);
        long versionId=id();
        try { mapper.insertVersion(versionId,packageId,command.versionNo(),command.name(),command.effectiveFrom());
            Set<String> seen=new HashSet<>();
            for(var module:command.modules()) {
                if(!seen.add(module.moduleCode()) || mapper.insertModule(versionId,module.moduleCode(),module.enabled(),write(module.quotas()))!=1)
                    throw new ApiException(PackagePlanErrorCode.INVALID,"模块不存在、停用或重复: "+module.moduleCode());
            }
        } catch(DuplicateKeyException e){ throw new ApiException(PackagePlanErrorCode.DUPLICATE); }
        return versionView(mapper.findVersion(versionId));
    }
    @Transactional
    public PackagePlanModels.VersionView updateVersion(long id,PackagePlanModels.UpdateVersion command){
        var old=requireVersion(id);requireEditableParent(old.packageId());old=mapper.lockVersion(id);
        if(!"DRAFT".equals(old.status())||old.version()!=command.version())throw new ApiException(CommonErrorCode.CONFLICT,"草稿已变化或已发布，请重新读取");
        if(mapper.updateVersion(id,command.name(),command.version())!=1)throw new ApiException(CommonErrorCode.CONFLICT);
        mapper.deleteModules(id);Set<String> seen=new HashSet<>();
        for(var module:command.modules())if(!seen.add(module.moduleCode())||mapper.insertModule(id,module.moduleCode(),module.enabled(),write(module.quotas()))!=1)
            throw new ApiException(PackagePlanErrorCode.INVALID,"模块不存在、停用或重复: "+module.moduleCode());
        audit.append(new AuditService.AuditCommand("PACKAGE_VERSION_UPDATE","PACKAGE_VERSION",id,null,Map.of("version",old.version()),Map.of("version",old.version()+1,"name",command.name()),null,null));
        return versionView(mapper.findVersion(id));
    }
    private PackagePlanModels.PackageRow requireEditableParent(long id){var p=mapper.lockPackage(id);if(p==null)throw new ApiException(CommonErrorCode.NOT_FOUND);if(!Set.of("DRAFT","ACTIVE").contains(p.status()))throw new ApiException(PackagePlanErrorCode.INVALID,"父套餐已失效，不能配置或发布版本");return p;}
    public PackagePlanModels.ValidationResult validate(long versionId) {var v=requireVersion(versionId);return validate(v,mapper.listModules(versionId),mapper.moduleOptions(),mapper.findPackage(v.packageId()));}
    private PackagePlanModels.ValidationResult validate(PackagePlanModels.VersionRow version,List<PackagePlanModels.ModuleRow> modules,List<PackagePlanModels.ModuleOption> options,PackagePlanModels.PackageRow parent) {
        List<String> errors=new ArrayList<>();
        if(parent==null||!Set.of("DRAFT","ACTIVE").contains(parent.status()))errors.add("父套餐已失效");
        Set<String> available=options.stream().map(PackagePlanModels.ModuleOption::code).collect(java.util.stream.Collectors.toSet());
        modules.stream().filter(m->!available.contains(m.moduleCode())).forEach(m->errors.add("模块已停用: "+m.moduleCode()));
        if(modules.stream().noneMatch(PackagePlanModels.ModuleRow::enabled)) errors.add("至少启用一个模块");
        modules.forEach(m->readQuotas(m.quotaJson()).forEach((code,limit)->{ if(limit<0) errors.add(code+" 不能小于0"); }));
        if(!"DRAFT".equals(version.status())) errors.add("仅草稿版本可以发布");
        return new PackagePlanModels.ValidationResult(errors.isEmpty(),List.copyOf(errors));
    }
    @Transactional
    public PackagePlanModels.VersionView publish(long versionId,int version,long actorId) {
        var original=requireVersion(versionId);var parent=requireEditableParent(original.packageId());var current=mapper.lockVersion(versionId);
        if(current.version()!=version||!"DRAFT".equals(current.status()))throw new ApiException(CommonErrorCode.CONFLICT,"套餐版本已变化或不可发布");
        PackagePlanModels.ValidationResult result=validate(current,mapper.lockModules(versionId),mapper.lockModuleOptions(),parent); if(!result.valid()) throw new ApiException(PackagePlanErrorCode.INVALID,String.join("；",result.errors()));
        if(mapper.publish(versionId,version,actorId,LocalDateTime.now(ZoneOffset.UTC))!=1) throw new ApiException(CommonErrorCode.CONFLICT,"套餐版本已变化或不可发布");
        mapper.activatePackage(original.packageId());
        return versionView(mapper.lockVersion(versionId),mapper.lockModules(versionId));
    }
    @Transactional
    public PackagePlanModels.SubscriptionPreview preview(long tenantId,long targetVersionId) {
        var tenant=lockAssignableTenant(tenantId);var now=mapper.utcNow();
        if(mapper.lockAssignableVersion(targetVersionId)==null)throw new ApiException(PackagePlanErrorCode.INVALID,"目标套餐或已发布版本不可用");
        PackagePlanModels.VersionRow target=requirePublished(targetVersionId); Long currentId=mapper.findTenantPackageVersion(tenantId);
        Map<String,PackagePlanModels.ModuleGrant> current=currentId==null?Map.of():grantMap(currentId); Map<String,PackagePlanModels.ModuleGrant> next=grantMap(target.id());
        Set<String> removed=current.entrySet().stream().filter(e->e.getValue().enabled()).map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        removed.removeAll(next.entrySet().stream().filter(e->e.getValue().enabled()).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()));
        Map<String,PackagePlanModels.QuotaChange> changes=new TreeMap<>();
        Map<String,Long> oldQ=flatten(current.values()), newQ=flatten(next.values());
        Set<String> quotaCodes=new TreeSet<>(oldQ.keySet());
        quotaCodes.addAll(newQ.keySet()); quotaCodes.addAll(mapper.usedQuotaCodes(tenantId));quotaCodes.addAll(usageReaders.keySet());
        for(String code:quotaCodes){ long old=oldQ.getOrDefault(code,0L), limit=newQ.getOrDefault(code,0L), used=used(tenantId,code); if(limit<old || used>limit) changes.put(code,new PackagePlanModels.QuotaChange(old,limit,used,used>limit)); }
        List<String> advice=new ArrayList<>(); if(!removed.isEmpty()) advice.add("确认失效模块的数据只读保留策略");
        if(changes.values().stream().anyMatch(PackagePlanModels.QuotaChange::exceedsTarget)) advice.add("先清理超出目标额度的资源或确认只读保留");
        return new PackagePlanModels.SubscriptionPreview(Long.toString(tenantId),currentId==null?null:currentId.toString(),Long.toString(targetVersionId),removed,changes,advice,tenant.version(),now,mapper.futurePlanCount(tenantId,now),capabilityCatalog.compare(
                Set.copyOf(mapper.currentEnabledTenantModules(tenantId)),next.values().stream()
                .filter(PackagePlanModels.ModuleGrant::enabled).map(PackagePlanModels.ModuleGrant::moduleCode)
                .filter(mapper.lockModuleOptions().stream().map(PackagePlanModels.ModuleOption::code).collect(java.util.stream.Collectors.toSet())::contains)
                .collect(java.util.stream.Collectors.toSet())));
    }
    @Transactional
    public PackagePlanModels.SubscriptionPreview assign(long tenantId,long targetVersionId,PackagePlanModels.AssignSubscription command) {
        if(Long.parseLong(command.packageVersionId())!=targetVersionId) throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        var tenant=lockAssignableTenant(tenantId);
        if(command.tenantVersion()!=null&&command.tenantVersion()!=tenant.version())throw new ApiException(CommonErrorCode.CONFLICT,"租户订阅已变化，请重新预览后确认");
        LocalDateTime now=mapper.utcNow();
        if(command.effectiveFrom()!=null&&(command.effectiveFrom().getYear()<1000||command.effectiveFrom().getYear()>9999)||command.effectiveTo()!=null&&(command.effectiveTo().getYear()<1000||command.effectiveTo().getYear()>9999))throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"订阅日期超出数据库支持范围");
        if(command.effectiveFrom()==null || (command.effectiveTo()!=null &&
                (!command.effectiveTo().isAfter(command.effectiveFrom()) || !command.effectiveTo().isAfter(now))))
            throw new ApiException(PackagePlanErrorCode.INVALID,"订阅结束时间必须晚于起始时间和当前 UTC 时间");
        if(command.activationMode()==PackagePlanModels.ActivationMode.SCHEDULED&&!command.effectiveFrom().isAfter(now))
            throw new ApiException(PackagePlanErrorCode.INVALID,"未来生效时间已过，请重新选择并预览");
        if(command.activationMode()==PackagePlanModels.ActivationMode.IMMEDIATE&&command.effectiveFrom().isAfter(now))
            throw new ApiException(PackagePlanErrorCode.INVALID,"立即生效不能指定未来起始时间");
        if(!command.exceptions().isEmpty())
            throw new ApiException(PackagePlanErrorCode.INVALID,"当前尚不支持套餐例外授权，不能忽略已提交的例外配置");
        PackagePlanModels.SubscriptionPreview preview=preview(tenantId,targetVersionId);
        if(preview.quotaChanges().values().stream().anyMatch(PackagePlanModels.QuotaChange::exceedsTarget)) throw new ApiException(PackagePlanErrorCode.QUOTA_EXCEEDED,"目标额度低于当前使用量，不能直接分配");
        // Past starts mean immediate replacement; never rewrite the historical subscription window.
        LocalDateTime from=command.effectiveFrom().isBefore(now)?now:command.effectiveFrom();
        mapper.materializeLegacySubscription(tenantId,id(),now);
        mapper.cancelFutureSubscriptions(tenantId,now);
        mapper.insertSubscription(id(),tenantId,targetVersionId,from,command.effectiveTo(),write(command.exceptions()));
        if(mapper.updateTenantPackage(tenantId,targetVersionId)!=1) throw new ApiException(CommonErrorCode.NOT_FOUND); return preview;
    }
    public void requireModule(long tenantId,String moduleCode){ if(!mapper.enabledTenantModules(tenantId).contains(moduleCode)) throw new ApiException(PackagePlanErrorCode.MODULE_UNAVAILABLE); }
    public PackagePlanModels.QuotaUsage quota(long tenantId,String quotaCode){ requireQuotaTenant(tenantId);if(quotaCode==null||!quotaCode.matches("[A-Z][A-Z0-9_]{0,63}"))throw new ApiException(CommonErrorCode.VALIDATION_FAILED);Long version=mapper.findTenantPackageVersion(tenantId); long limit=version==null?0:flatten(grantMap(version).values()).getOrDefault(quotaCode,0L),used=used(tenantId,quotaCode); return new PackagePlanModels.QuotaUsage(quotaCode,limit,used,Math.max(0,limit-used),limit>0&&used>=limit-limit/5,used>=limit); }
    public void requireQuota(long tenantId,String quotaCode,long delta){ var q=quota(tenantId,quotaCode); if(q.limit()<=0||delta<0||delta>q.limit()||q.used()>q.limit()-delta) throw new ApiException(PackagePlanErrorCode.QUOTA_EXCEEDED); }

    private PackagePlanModels.PackageView view(PackagePlanModels.PackageRow row){return new PackagePlanModels.PackageView(Long.toString(row.id()),row.packageCode(),row.packageName(),row.status(),row.version(),mapper.listVersions(row.id()).stream().map(this::versionView).toList());}
    private PackagePlanModels.VersionView versionView(PackagePlanModels.VersionRow r){return versionView(r,mapper.listModules(r.id()));}
    private PackagePlanModels.VersionView versionView(PackagePlanModels.VersionRow r,List<PackagePlanModels.ModuleRow> modules){return new PackagePlanModels.VersionView(Long.toString(r.id()),r.versionNo(),r.versionName(),r.effectiveFrom(),r.status(),r.version(),modules.stream().map(m->new PackagePlanModels.ModuleGrant(m.moduleCode(),m.enabled(),readQuotas(m.quotaJson()))).toList());}
    private PackagePlanModels.VersionRow requireVersion(long id){var r=mapper.findVersion(id);if(r==null)throw new ApiException(CommonErrorCode.NOT_FOUND);return r;}
    private PackagePlanModels.VersionRow requirePublished(long id){var r=requireVersion(id);if(!"PUBLISHED".equals(r.status()))throw new ApiException(PackagePlanErrorCode.INVALID,"只能分配已发布版本");return r;}
    private Map<String,PackagePlanModels.ModuleGrant> grantMap(long id){Map<String,PackagePlanModels.ModuleGrant> map=new HashMap<>();for(var m:mapper.listModules(id))map.put(m.moduleCode(),new PackagePlanModels.ModuleGrant(m.moduleCode(),m.enabled(),readQuotas(m.quotaJson())));return map;}
    private Map<String,Long> flatten(Collection<PackagePlanModels.ModuleGrant> grants){Map<String,Long> r=new HashMap<>();Set<String> active=mapper.moduleOptions().stream().map(PackagePlanModels.ModuleOption::code).collect(java.util.stream.Collectors.toSet());grants.stream().filter(g->g.enabled()&&active.contains(g.moduleCode())).forEach(g->g.quotas().forEach((k,v)->r.merge(k,v,Math::max)));return r;}
    private PackagePlanModels.SubscriptionTenantRow lockAssignableTenant(long id){
        if(id<=0)throw new ApiException(CommonErrorCode.NOT_FOUND);
        var row=mapper.lockQuotaTenant(id);if(row==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(Set.of("CANCELLED","CANCELLING").contains(row.status()))throw new ApiException(PackagePlanErrorCode.INVALID,"注销租户不能分配套餐");
        return mapper.lockSubscriptionTenant(id);
    }
    private void requireQuotaTenant(long id){if(id<=0||mapper.subscriptionTenant(id)==null)throw new ApiException(CommonErrorCode.NOT_FOUND);}
    private long used(long tenant,String code){if(usageReaders.containsKey(code))return usageReaders.get(code).used(tenant);Long v=mapper.quotaUsed(tenant,code,"CURRENT");return v==null?0:v;}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(JsonProcessingException e){throw new IllegalArgumentException(e);}}
    private Map<String,Long> readQuotas(String v){try{return json.readValue(v,new TypeReference<>(){});}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private static long id(){return RANDOM.nextLong(Long.MAX_VALUE-1)+1;}
}
