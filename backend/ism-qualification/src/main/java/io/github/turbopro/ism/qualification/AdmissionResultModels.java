package io.github.turbopro.ism.qualification;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
public final class AdmissionResultModels {
 private AdmissionResultModels(){}
 public record Publish(@NotNull @PositiveOrZero Integer version,@NotBlank @Size(max=1000) String publicComment){}
 public record Review(long id,String action,String toStatus,Integer applicationVersion,int sameInstantCount){}
 public record View(String id,String applicationNo,String purchaseCategory,String result,String publicComment,
  @com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") LocalDateTime publishedAt){}
 public record EmployeePage(boolean canPublish,List<View> items){}
 public record Page(long total,int page,int size,List<View> items){}
}
