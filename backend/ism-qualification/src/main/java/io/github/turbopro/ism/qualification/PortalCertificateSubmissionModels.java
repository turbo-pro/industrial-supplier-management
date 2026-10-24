package io.github.turbopro.ism.qualification;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class PortalCertificateSubmissionModels {
    private PortalCertificateSubmissionModels(){}
    public record Submit(@NotBlank @Pattern(regexp="[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}") String requestKey,
        @NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String typeId,@NotBlank @Size(max=100) String certificateNo,
        @Size(max=200) String issuingAuthority,LocalDate issueDate,LocalDate effectiveDate,LocalDate expiryDate,boolean permanent,
        @NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String uploadId){
        public Submit{certificateNo=certificateNo==null?null:certificateNo.trim();issuingAuthority=issuingAuthority==null?"":issuingAuthority.trim();}
    }
    public record Type(String id,String code,String name,boolean validityRequired){}
    public record Row(long qualificationId,long accountId,String requestJson,String requestKey,String fileName,LocalDateTime createdAt){}
    public record View(String id,String requestKey,String typeName,String certificateNo,String status,String fileName,LocalDateTime submittedAt){}
    public record Page(long total,int page,int size,List<View> items){}
}
