package io.github.turbopro.ism.qualification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class AdmissionPortalModels {
 private AdmissionPortalModels(){}
 public record Publish(@NotNull @PositiveOrZero Integer version,@NotBlank @Size(max=1000) String instruction,
  @NotEmpty @Size(max=5) List<@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,63}") String> types){}
 public record Version(@NotNull @PositiveOrZero Integer version){}
 public record Cancel(@NotNull @PositiveOrZero Integer version,@NotBlank @Size(max=500) String reason){}
 public record Material(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,63}") String type,@NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String uploadId){}
 public record Submit(@NotNull @PositiveOrZero Integer version,@NotEmpty @Size(max=5) List<@NotNull @Valid Material> materials){}
 public record Row(long id,long applicationId,long supplierId,int applicationVersion,String applicationNo,String instruction,String state,int version,Long submittedBy,String submissionJson,String closeNote,LocalDateTime createdAt,LocalDateTime submittedAt){}
 public record ItemRow(String materialType,String materialName,Long fileId,String fileName,int sortOrder){}
 public record Item(String type,String name,String fileName){}
 public record View(String id,String applicationNo,String instruction,String state,int version,String closeNote,
  @com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") LocalDateTime createdAt,
  @com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") LocalDateTime submittedAt,List<Item> items){}
 public record EmployeePage(boolean canManage,boolean canDownload,List<View> items){}
 public record Page(long total,int page,int size,List<View> items){}
}
