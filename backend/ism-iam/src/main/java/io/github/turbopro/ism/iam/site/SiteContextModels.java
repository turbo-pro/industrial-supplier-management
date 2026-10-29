package io.github.turbopro.ism.iam.site;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
public final class SiteContextModels {
    private SiteContextModels(){}
    public record Select(@Pattern(regexp="[1-9][0-9]{0,18}")String siteId){}
    public record Issued(SitePreferenceModels.Option site,String contextToken,Instant expiresAt,String selectionReason){}
    public record Current(SitePreferenceModels.Option site,Instant expiresAt){}
}
