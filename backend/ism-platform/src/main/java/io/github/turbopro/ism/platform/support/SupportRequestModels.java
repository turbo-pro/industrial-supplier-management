package io.github.turbopro.ism.platform.support;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
public final class SupportRequestModels {
 private SupportRequestModels(){}
 public record Create(@NotBlank @Size(max=100) String ticketNo,@NotBlank @Size(max=1000) String reason,
                      @NotBlank @Pattern(regexp="SUPPLIER_BASIC_READ_V1") String profileCode,
                      @NotNull @Min(5) @Max(60) Integer durationMinutes){}
 public record Change(@NotNull @PositiveOrZero Integer version,@NotBlank @Size(max=1000) String comment){}
 public record Profile(String code,String temporaryRole,String dataScope,List<String> actions,List<String> fields){}
 public record Tenant(long id,String status,String initializationStatus){}
 public record Row(long id,String bizNo,long platformOperatorId,String operatorName,String operatorStatus,long targetTenantId,
                   String tenantCode,String tenantName,String ticketNo,String reason,String profileJson,
                   LocalDateTime requestedExpireAt,String status,LocalDateTime submittedAt,int version,LocalDateTime createdAt){}
 public record Event(String id,String actorId,String actorName,int requestVersion,String action,String comment,LocalDateTime occurredAt){}
 public record View(String id,String bizNo,String operatorId,String operatorName,String tenantId,String tenantCode,
                    String tenantName,String ticketNo,String reason,Profile profile,LocalDateTime requestedExpireAt,
                    String status,LocalDateTime submittedAt,int version,LocalDateTime createdAt,
                    boolean canSubmit,boolean canCancel,boolean canReview,List<Event> events){}
 public record Page(long total,int page,int size,List<View> items){}
}
