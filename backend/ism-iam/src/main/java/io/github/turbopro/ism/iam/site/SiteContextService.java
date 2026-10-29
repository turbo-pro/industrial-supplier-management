package io.github.turbopro.ism.iam.site;
import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.authorization.AuthorizationContext;
import io.github.turbopro.ism.common.infrastructure.tenant.*;
import io.github.turbopro.ism.iam.auth.JwtTokenService;
import io.github.turbopro.ism.operation.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
@Service
public class SiteContextService {
    private final SitePreferenceService preferences;private final SiteContextTokens tokens;private final JwtTokenService accessTokens;private final AuditService audit;
    public SiteContextService(SitePreferenceService preferences,SiteContextTokens tokens,JwtTokenService accessTokens,AuditService audit){this.preferences=preferences;this.tokens=tokens;this.accessTokens=accessTokens;this.audit=audit;}
    @Transactional
    public SiteContextModels.Issued issue(String bearer,SiteContextModels.Select command){
        var who=TenantContext.require();var access=accessTokens.decode(bearer);
        if(!Long.toString(who.actorId()).equals(access.getSubject())||!Long.toString(who.tenantId()).equals(access.getClaimAsString("tenantId")))throw new ApiException(SiteContextError.INVALID);
        String reason="EXPLICIT";SitePreferenceModels.Option site;
        if(command.siteId()!=null){try{site=preferences.requireOption(Long.parseLong(command.siteId()));}catch(NumberFormatException e){throw new ApiException(CommonErrorCode.VALIDATION_FAILED);}}
        else{
            var preferred=preferences.state();if(preferred.unavailable())throw new ApiException(CommonErrorCode.RULE_BLOCKED,"原首选场站已不可用，请明确选择场站");
            if(preferred.preferredSite()!=null){site=preferred.preferredSite();reason="PREFERRED";}
            else{var candidates=preferences.options("",0,2);if(candidates.total()!=1)throw new ApiException(CommonErrorCode.RULE_BLOCKED,candidates.total()==0?"没有有效授权场站":"存在多个授权场站，请明确选择");site=candidates.items().get(0);reason="ONLY_SITE";}
        }
        var token=tokens.issue(who.tenantId(),who.actorId(),Long.parseLong(site.id()),bearer,access.getExpiresAt());
        audit.append(new AuditService.AuditCommand("SITE_CONTEXT_ISSUE","ORGANIZATION",Long.parseLong(site.id()),null,Map.of(),Map.of("selectionReason",reason,"expiresAt",token.expiresAt().toString()),null,null));
        return new SiteContextModels.Issued(site,token.value(),token.expiresAt(),reason);
    }
    public SiteContextTokens.Verified resolve(String contextToken,String bearer){
        if(!AuthorizationContext.require().hasAction("iam:site:view"))throw new ApiException(SiteContextError.UNAVAILABLE);
        var who=TenantContext.require();var result=tokens.verify(contextToken,bearer,who.tenantId(),who.actorId());
        try{preferences.requireOption(result.siteId());}catch(ApiException denied){throw new ApiException(SiteContextError.UNAVAILABLE);}
        return result;
    }
    public SiteContextModels.Current current(){var current=SiteContext.require();return new SiteContextModels.Current(preferences.requireOption(current.siteId()),current.expiresAt());}
}
