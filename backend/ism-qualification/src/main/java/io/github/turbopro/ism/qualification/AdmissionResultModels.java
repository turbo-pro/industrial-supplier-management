package io.github.turbopro.ism.qualification;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
public final class AdmissionResultModels {
 private AdmissionResultModels(){}
 public record Publish(@NotNull @PositiveOrZero Integer version,@NotBlank @Size(max=1000) String publicComment){}
 public record Correct(@NotNull @Min(0) @Max(2147483646) Integer version,@NotBlank @Size(max=1000) String publicComment,@NotBlank @Size(max=500) String reason){}
 public record Withdraw(@NotNull @Min(0) @Max(2147483646) Integer version,@NotBlank @Size(max=500) String reason){}
 public record Source(long applicationId,long supplierId,int changeVersion){}
 public record ChangeRow(int changeNo,String kind,String publicComment,String publicReason){}
 public record Change(int version,String kind,String publicComment,String reason,@com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") LocalDateTime changedAt){}
 public record History(String originalComment,long total,int page,int size,List<Change> items){}
 public record Review(long id,String action,String toStatus,Integer applicationVersion,int sameInstantCount){}
 public record View(String id,String applicationNo,String purchaseCategory,String result,String publicComment,
  @com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") LocalDateTime publishedAt,String publicationState,int publicationVersion,String adjustmentReason,
  @com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") LocalDateTime adjustedAt){}
 public record EmployeePage(boolean canPublish,boolean canAdjust,List<View> items){}
 public record Page(long total,int page,int size,List<View> items){}
}
