package io.github.turbopro.ism.supplier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class ProfileChangeModels {
    private ProfileChangeModels(){}
    public record Fields(@Size(max=100) String shortName,@Size(max=100) String industry,
        @NotBlank @Pattern(regexp="[A-Z]{2}") String countryCode,@Size(max=100) String province,
        @Size(max=100) String city,@Size(max=300) String address,@Size(max=300) String website){
        public Fields {shortName=clean(shortName);industry=clean(industry);countryCode=clean(countryCode);province=clean(province);city=clean(city);address=clean(address);website=clean(website);}
    }
    public record Submit(@NotBlank @Pattern(regexp="[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}") String requestKey,
        @NotNull @Min(0) Integer baseVersion,@NotNull @Valid Fields fields,@NotBlank @Size(max=1000) String reason){}
    public enum Decision { APPROVE, REJECT }
    public record Review(@NotNull Decision decision,@NotBlank @Size(max=1000) String comment,@NotNull @Min(0) Integer version){}
    public record Version(@NotNull @Min(0) Integer version){}
    public record Row(long id,long supplierId,String requestKey,int baseVersion,String beforeJson,String afterJson,String reason,
        String status,long createdBy,LocalDateTime createdAt,LocalDateTime reviewedAt,String reviewComment,int version){}
    public record View(String id,String requestKey,int baseVersion,Fields before,Fields requested,String reason,String status,
        LocalDateTime createdAt,LocalDateTime reviewedAt,String reviewComment,int version,boolean canCancel){}
    public record Page(long total,int page,int size,boolean canReview,List<View> items){}
    static String clean(String value){return value==null?"":value.trim();}
    static Fields fields(SupplierModels.SupplierRow row){return new Fields(row.shortName(),row.industry(),row.countryCode(),row.province(),row.city(),row.address(),row.website());}
}
