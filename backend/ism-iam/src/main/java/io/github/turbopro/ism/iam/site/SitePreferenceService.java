package io.github.turbopro.ism.iam.site;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class SitePreferenceService {
    private final SitePreferenceMapper mapper;
    public SitePreferenceService(SitePreferenceMapper mapper){this.mapper=mapper;}
    @Transactional(readOnly=true)
    public SitePreferenceModels.State state(){
        var who=TenantContext.require();var row=mapper.preference(who.tenantId(),who.actorId());
        if(row==null)return new SitePreferenceModels.State(null,false,0,AuthorizationContext.require().hasAction("iam:site:preference"));
        var option=row.preferredSiteId()==null?null:eligible(row.preferredSiteId());
        return new SitePreferenceModels.State(option,row.preferredSiteId()!=null&&option==null,row.version(),AuthorizationContext.require().hasAction("iam:site:preference"));
    }
    @Transactional(readOnly=true)
    public SitePreferenceModels.Page options(String q,int page,int size){
        if(q==null||q.length()>100||page<0||size<1||size>100)throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        var who=TenantContext.require();var scope=AuthorizationContext.require().dataScope("iam:site");
        String query="%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        return new SitePreferenceModels.Page(mapper.options(who.tenantId(),who.actorId(),scope.type().name(),scope.organizationIds(),query,size,(long)page*size),mapper.count(who.tenantId(),who.actorId(),scope.type().name(),scope.organizationIds(),query),page,size);
    }
    public void requireTarget(String value){if(value!=null&&eligible(parseId(value))==null)throw new ApiException(CommonErrorCode.FORBIDDEN,"该场站当前不在可选范围内");}
    private SitePreferenceModels.Option eligible(long site){var who=TenantContext.require();var scope=AuthorizationContext.require().dataScope("iam:site");return mapper.eligible(who.tenantId(),who.actorId(),scope.type().name(),scope.organizationIds(),site);}
    private long parseId(String value){try{long id=Long.parseLong(value);if(id<=0)throw new NumberFormatException();return id;}catch(NumberFormatException e){throw new ApiException(CommonErrorCode.VALIDATION_FAILED);}}
    @Transactional
    public SitePreferenceModels.Changed save(SitePreferenceModels.Update command){
        var who=TenantContext.require();if(mapper.lockUser(who.tenantId(),who.actorId())==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        var old=mapper.preferenceForUpdate(who.tenantId(),who.actorId());int version=old==null?0:old.version();
        if(command.version()==null||version!=command.version())throw new ApiException(CommonErrorCode.CONFLICT,"首选场站版本已变化，请重新读取后保存");
        requireTarget(command.siteId());Long site=command.siteId()==null?null:parseId(command.siteId());
        if(old==null)mapper.insert(who.tenantId(),who.actorId(),site);
        else if(mapper.update(who.tenantId(),who.actorId(),site,version)!=1)throw new ApiException(CommonErrorCode.CONFLICT,"首选场站版本已变化，请重新读取后保存");
        return new SitePreferenceModels.Changed(state(),old==null||old.preferredSiteId()==null?"":old.preferredSiteId().toString(),site==null?"":site.toString());
    }
}
