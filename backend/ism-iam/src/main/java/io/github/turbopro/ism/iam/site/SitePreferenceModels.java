package io.github.turbopro.ism.iam.site;
import jakarta.validation.constraints.*;
import java.util.List;
public final class SitePreferenceModels {
    private SitePreferenceModels(){}
    public record Option(String id,String code,String name){}
    public record State(Option preferredSite,boolean unavailable,int version,boolean canManage){}
    public record Page(List<Option> items,long total,int page,int size){}
    public record Update(@Pattern(regexp="[1-9][0-9]{0,18}") String siteId,@NotNull @PositiveOrZero Integer version){}
    public record Row(Long preferredSiteId,int version){}
    public record Changed(State state,String beforeSiteId,String siteId){}
}
