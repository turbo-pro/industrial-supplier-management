package io.github.turbopro.ism.qualification;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class PortalCertificateSubmissionModels {
    private PortalCertificateSubmissionModels(){}
    public record Submit(@NotBlank @Pattern(regexp="[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}") String requestKey,
        @NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String typeId,@NotBlank @Size(max=100) String certificateNo,
        @Size(max=200) String issuingAuthority,LocalDate issueDate,LocalDate effectiveDate,LocalDate expiryDate,boolean permanent,
        @NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String uploadId,
        @Pattern(regexp="[1-9][0-9]{0,18}") String renewalParentId,@Min(0) Integer renewalParentVersion,
        @Pattern(regexp="[1-9][0-9]{0,18}") String correctionOfId,@Min(0) Integer correctionOfVersion,@Size(max=1000) String correctionNote,@Size(max=10) List<@NotBlank @Size(max=500) String> correctionResponses,@Size(max=5) List<@jakarta.validation.Valid Material> correctionAttachments){
        public Submit{correctionAttachments=correctionAttachments==null?List.of():List.copyOf(correctionAttachments);correctionResponses=correctionResponses==null?List.of():java.util.Collections.unmodifiableList(new java.util.ArrayList<>(correctionResponses));certificateNo=certificateNo==null?null:certificateNo.trim();issuingAuthority=issuingAuthority==null?"":issuingAuthority.trim();}
        public Submit(String requestKey,String typeId,String certificateNo,String issuingAuthority,LocalDate issueDate,LocalDate effectiveDate,LocalDate expiryDate,boolean permanent,String uploadId,String renewalParentId,Integer renewalParentVersion,String correctionOfId,Integer correctionOfVersion,String correctionNote,List<String> correctionResponses){this(requestKey,typeId,certificateNo,issuingAuthority,issueDate,effectiveDate,expiryDate,permanent,uploadId,renewalParentId,renewalParentVersion,correctionOfId,correctionOfVersion,correctionNote,correctionResponses,null);}
        public Submit(String requestKey,String typeId,String certificateNo,String issuingAuthority,LocalDate issueDate,LocalDate effectiveDate,LocalDate expiryDate,boolean permanent,String uploadId,String renewalParentId,Integer renewalParentVersion,String correctionOfId,Integer correctionOfVersion,String correctionNote){this(requestKey,typeId,certificateNo,issuingAuthority,issueDate,effectiveDate,expiryDate,permanent,uploadId,renewalParentId,renewalParentVersion,correctionOfId,correctionOfVersion,correctionNote,null);}
        public Submit(String requestKey,String typeId,String certificateNo,String issuingAuthority,LocalDate issueDate,LocalDate effectiveDate,LocalDate expiryDate,boolean permanent,String uploadId,String renewalParentId,Integer renewalParentVersion){this(requestKey,typeId,certificateNo,issuingAuthority,issueDate,effectiveDate,expiryDate,permanent,uploadId,renewalParentId,renewalParentVersion,null,null,null);}
        public Submit(String requestKey,String typeId,String certificateNo,String issuingAuthority,LocalDate issueDate,LocalDate effectiveDate,LocalDate expiryDate,boolean permanent,String uploadId){this(requestKey,typeId,certificateNo,issuingAuthority,issueDate,effectiveDate,expiryDate,permanent,uploadId,null,null);}
    }
    public record Withdraw(@NotNull @PositiveOrZero Integer version,@NotBlank @Size(max=500) String reason){}
    public record Withdrawal(long accountId,int sourceVersion,String reason){}
    public record WithdrawalTarget(long accountId,String status,int version){}
    public record Material(@NotNull @Min(0) @Max(9) Integer requirementIndex,@NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String uploadId){}
    public record Attachment(String id,int requirementIndex,String fileName){}
    public record AttachmentFile(long fileId,String fileName){}
    public record Type(String id,String code,String name,boolean validityRequired){}
    public record Row(long qualificationId,long accountId,String requestJson,String requestKey,String fileName,LocalDateTime createdAt){}
    public record View(String id,String requestKey,String typeName,String certificateNo,String status,String fileName,LocalDateTime submittedAt,String parentQualificationId,String parentCertificateNo,String publicReviewComment,@com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") LocalDateTime reviewedAt,
        String typeId,String issuingAuthority,LocalDate issueDate,LocalDate effectiveDate,LocalDate expiryDate,boolean permanent,int version,
        String correctionOfId,String correctionRootId,int correctionRound,String correctionNote,String correctionChildId,LocalDate correctionDueDate,
        @com.fasterxml.jackson.annotation.JsonIgnore String correctionRequirementsJson,
        LocalDate sourceCorrectionDueDate,@com.fasterxml.jackson.annotation.JsonIgnore String sourceRequirementsJson,
        @com.fasterxml.jackson.annotation.JsonIgnore String correctionResponsesJson,boolean lateCorrection,@com.fasterxml.jackson.annotation.JsonIgnore String attachmentRequiredJson,@com.fasterxml.jackson.annotation.JsonIgnore String sourceAttachmentRequiredJson,
        boolean withdrawable,String withdrawalReason,@com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") LocalDateTime withdrawnAt){
        @com.fasterxml.jackson.annotation.JsonProperty public List<String> correctionRequirements(){return CorrectionJson.read(correctionRequirementsJson);}
        @com.fasterxml.jackson.annotation.JsonProperty public List<String> sourceCorrectionRequirements(){return CorrectionJson.read(sourceRequirementsJson);}
        @com.fasterxml.jackson.annotation.JsonProperty public List<String> correctionResponses(){return CorrectionJson.read(correctionResponsesJson);}
        @com.fasterxml.jackson.annotation.JsonProperty public List<Integer> attachmentRequiredFor(){return CorrectionJson.readInts(attachmentRequiredJson);}
        @com.fasterxml.jackson.annotation.JsonProperty public List<Integer> sourceAttachmentRequiredFor(){return CorrectionJson.readInts(sourceAttachmentRequiredJson);}
        @com.fasterxml.jackson.annotation.JsonProperty public boolean correctionOverdue(){return status.equals("REJECTED")&&correctionChildId==null&&correctionDueDate!=null&&correctionDueDate.isBefore(CorrectionJson.today());}
    }
    public record CorrectionSource(long id,long typeId,String status,int version,long rootId,int roundNo,Long renewalParentId,Integer renewalParentVersion,LocalDate correctionDueDate,String requirementsJson,String attachmentRequiredJson){}
    public record CorrectionInfo(String correctionOfId,int correctionRound,String correctionNote,LocalDate sourceDueDate,String sourceRequirementsJson,String responsesJson,boolean lateCorrection,String attachmentRequiredJson){}
    public record ReviewPolicy(LocalDate dueDate,String requirementsJson,String attachmentRequiredJson){}
    public record Parent(long id,long supplierId,long typeId,long organizationId,long createdBy,String status,int version){}
    public record Renewal(long parentId,int parentVersion,String state){}
    public record Page(long total,int page,int size,List<View> items){}
}
