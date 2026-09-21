package io.github.turbopro.ism.platform.packageplan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.api.error.*;
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
    public PackagePlanService(PackagePlanMapper mapper, ObjectMapper json) { this.mapper=mapper; this.json=json; }

    public List<PackagePlanModels.PackageView> list() { return mapper.listPackages().stream().map(this::view).toList(); }
    public PackagePlanModels.PackageView get(long id) {
        PackagePlanModels.PackageRow row=mapper.findPackage(id); if(row==null) throw new ApiException(CommonErrorCode.NOT_FOUND); return view(row);
    }
    @Transactional
    public PackagePlanModels.PackageView create(PackagePlanModels.CreatePackage command) {
        long id=id(); try { mapper.insertPackage(id,command.code(),command.name()); }
        catch(DuplicateKeyException e){ throw new ApiException(PackagePlanErrorCode.DUPLICATE); } return get(id);
    }
    @Transactional
    public PackagePlanModels.PackageView update(long id, PackagePlanModels.UpdatePackage command) {
        if(mapper.updatePackage(id,command.name(),command.version())!=1) throw new ApiException(CommonErrorCode.CONFLICT,"套餐不是可编辑草稿或版本已变化"); return get(id);
    }
    @Transactional
    public PackagePlanModels.VersionView createVersion(long packageId, PackagePlanModels.CreateVersion command) {
        PackagePlanModels.PackageRow plan=mapper.findPackage(packageId); if(plan==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
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
    public PackagePlanModels.ValidationResult validate(long versionId) {
        PackagePlanModels.VersionRow version=requireVersion(versionId); List<String> errors=new ArrayList<>();
        List<PackagePlanModels.ModuleRow> modules=mapper.listModules(versionId);
        if(modules.stream().noneMatch(PackagePlanModels.ModuleRow::enabled)) errors.add("至少启用一个模块");
        modules.forEach(m->readQuotas(m.quotaJson()).forEach((code,limit)->{ if(limit<0) errors.add(code+" 不能小于0"); }));
        if("PUBLISHED".equals(version.status())) errors.add("版本已经发布");
        return new PackagePlanModels.ValidationResult(errors.isEmpty(),List.copyOf(errors));
    }
    @Transactional
    public PackagePlanModels.VersionView publish(long versionId,int version,long actorId) {
        PackagePlanModels.ValidationResult result=validate(versionId); if(!result.valid()) throw new ApiException(PackagePlanErrorCode.INVALID,String.join("；",result.errors()));
        if(mapper.publish(versionId,version,actorId,LocalDateTime.now(ZoneOffset.UTC))!=1) throw new ApiException(CommonErrorCode.CONFLICT,"套餐版本已变化或不可发布");
        return versionView(mapper.findVersion(versionId));
    }
    public PackagePlanModels.SubscriptionPreview preview(long tenantId,long targetVersionId) {
        PackagePlanModels.VersionRow target=requirePublished(targetVersionId); Long currentId=mapper.findTenantPackageVersion(tenantId);
        Map<String,PackagePlanModels.ModuleGrant> current=currentId==null?Map.of():grantMap(currentId); Map<String,PackagePlanModels.ModuleGrant> next=grantMap(target.id());
        Set<String> removed=current.entrySet().stream().filter(e->e.getValue().enabled()).map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        removed.removeAll(next.entrySet().stream().filter(e->e.getValue().enabled()).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()));
        Map<String,PackagePlanModels.QuotaChange> changes=new TreeMap<>();
        Map<String,Long> oldQ=flatten(current.values()), newQ=flatten(next.values());
        for(String code:oldQ.keySet()){ long old=oldQ.get(code), limit=newQ.getOrDefault(code,0L); if(limit<old){long used=used(tenantId,code);changes.put(code,new PackagePlanModels.QuotaChange(old,limit,used,used>limit));}}
        List<String> advice=new ArrayList<>(); if(!removed.isEmpty()) advice.add("确认失效模块的数据只读保留策略");
        if(changes.values().stream().anyMatch(PackagePlanModels.QuotaChange::exceedsTarget)) advice.add("先清理超出目标额度的资源或确认只读保留");
        return new PackagePlanModels.SubscriptionPreview(Long.toString(tenantId),currentId==null?null:currentId.toString(),Long.toString(targetVersionId),removed,changes,advice);
    }
    @Transactional
    public PackagePlanModels.SubscriptionPreview assign(long tenantId,long targetVersionId,PackagePlanModels.AssignSubscription command) {
        if(Long.parseLong(command.packageVersionId())!=targetVersionId) throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        PackagePlanModels.SubscriptionPreview preview=preview(tenantId,targetVersionId);
        if(preview.quotaChanges().values().stream().anyMatch(PackagePlanModels.QuotaChange::exceedsTarget)) throw new ApiException(PackagePlanErrorCode.QUOTA_EXCEEDED,"目标额度低于当前使用量，不能直接分配");
        LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC); mapper.terminateSubscription(tenantId,now);
        mapper.insertSubscription(id(),tenantId,targetVersionId,command.effectiveFrom(),command.effectiveTo(),write(command.exceptions()));
        if(mapper.updateTenantPackage(tenantId,targetVersionId)!=1) throw new ApiException(CommonErrorCode.NOT_FOUND); return preview;
    }
    public void requireModule(long tenantId,String moduleCode){ Long version=mapper.findTenantPackageVersion(tenantId); if(version==null||!grantMap(version).getOrDefault(moduleCode,new PackagePlanModels.ModuleGrant(moduleCode,false,Map.of())).enabled()) throw new ApiException(PackagePlanErrorCode.MODULE_UNAVAILABLE); }
    public PackagePlanModels.QuotaUsage quota(long tenantId,String quotaCode){ Long version=mapper.findTenantPackageVersion(tenantId); long limit=version==null?0:flatten(grantMap(version).values()).getOrDefault(quotaCode,0L),used=used(tenantId,quotaCode); return new PackagePlanModels.QuotaUsage(quotaCode,limit,used,Math.max(0,limit-used),limit>0&&used*100>=limit*80,used>=limit); }
    public void requireQuota(long tenantId,String quotaCode,long delta){ var q=quota(tenantId,quotaCode); if(q.limit()<=0||q.used()+delta>q.limit()) throw new ApiException(PackagePlanErrorCode.QUOTA_EXCEEDED); }

    private PackagePlanModels.PackageView view(PackagePlanModels.PackageRow row){return new PackagePlanModels.PackageView(Long.toString(row.id()),row.packageCode(),row.packageName(),row.status(),row.version(),mapper.listVersions(row.id()).stream().map(this::versionView).toList());}
    private PackagePlanModels.VersionView versionView(PackagePlanModels.VersionRow r){return new PackagePlanModels.VersionView(Long.toString(r.id()),r.versionNo(),r.versionName(),r.effectiveFrom(),r.status(),r.version(),mapper.listModules(r.id()).stream().map(m->new PackagePlanModels.ModuleGrant(m.moduleCode(),m.enabled(),readQuotas(m.quotaJson()))).toList());}
    private PackagePlanModels.VersionRow requireVersion(long id){var r=mapper.findVersion(id);if(r==null)throw new ApiException(CommonErrorCode.NOT_FOUND);return r;}
    private PackagePlanModels.VersionRow requirePublished(long id){var r=requireVersion(id);if(!"PUBLISHED".equals(r.status()))throw new ApiException(PackagePlanErrorCode.INVALID,"只能分配已发布版本");return r;}
    private Map<String,PackagePlanModels.ModuleGrant> grantMap(long id){Map<String,PackagePlanModels.ModuleGrant> map=new HashMap<>();for(var m:mapper.listModules(id))map.put(m.moduleCode(),new PackagePlanModels.ModuleGrant(m.moduleCode(),m.enabled(),readQuotas(m.quotaJson())));return map;}
    private Map<String,Long> flatten(Collection<PackagePlanModels.ModuleGrant> grants){Map<String,Long> r=new HashMap<>();grants.stream().filter(PackagePlanModels.ModuleGrant::enabled).forEach(g->g.quotas().forEach((k,v)->r.merge(k,v,Math::max)));return r;}
    private long used(long tenant,String code){Long v=mapper.quotaUsed(tenant,code,"CURRENT");return v==null?0:v;}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(JsonProcessingException e){throw new IllegalArgumentException(e);}}
    private Map<String,Long> readQuotas(String v){try{return json.readValue(v,new TypeReference<>(){});}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private static long id(){return RANDOM.nextLong(Long.MAX_VALUE-1)+1;}
}
