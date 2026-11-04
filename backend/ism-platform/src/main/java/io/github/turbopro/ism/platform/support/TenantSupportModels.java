package io.github.turbopro.ism.platform.support;
import io.github.turbopro.ism.common.api.support.SupportRoleAuthority;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.List;
public final class TenantSupportModels {
 private TenantSupportModels(){}
 public record SavePolicy(@NotNull Boolean enabled,@Pattern(regexp="[1-9][0-9]{0,18}") String roleId,
  @NotEmpty @Size(max=5) Set<@NotNull @Pattern(regexp="id|code|name|type|status") String> fields,
  @NotNull @Min(5) @Max(60) Integer maxMinutes,@NotNull @PositiveOrZero Integer version){
  public SavePolicy { if(fields!=null&&fields.stream().noneMatch(java.util.Objects::isNull))fields=java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(fields)); }
 }
 public record Decision(@NotNull @PositiveOrZero Integer requestVersion,@NotNull @PositiveOrZero Integer policyVersion,
  @NotNull @PositiveOrZero Integer decisionVersion,@NotBlank @Size(max=1000) String comment,
  @Pattern(regexp="[0-9a-f]{64}") String scopeHash,LocalDateTime expiresAt){}
 public record Scope(boolean tenantAll,Set<String> organizations,Set<String> fields){}
 public record PolicyRow(long tenantId,boolean enabled,Long roleId,String allowedFieldsJson,int maxMinutes,int version){}
 public record Policy(boolean enabled,String roleId,SupportRoleAuthority.Role role,Set<String> fields,int maxMinutes,
  int version,boolean canManage,boolean canReview){}
 public record Consent(String id,int policyVersion,int requestVersion,String status,String roleId,Scope scope,
  LocalDateTime expiresAt,String reviewerId,String reviewerName,String comment,LocalDateTime decidedAt,int version,
  String revokedBy,String revokeReason,LocalDateTime revokedAt){}
 public record ConsentRow(long id,long requestId,long tenantId,int policyVersion,int requestVersion,String status,
  Long roleId,String scopeJson,LocalDateTime expiresAt,long reviewerUserId,String reviewerName,String comment,
  LocalDateTime decidedAt,int version,Long revokedBy,String revokeReason,LocalDateTime revokedAt){}
 public record Request(String id,String bizNo,String operatorName,String ticketNo,String reason,String status,
  LocalDateTime requestedExpireAt,int version,int policyVersion,Consent consent,List<Consent> history,Scope proposedScope,String scopeHash,LocalDateTime proposedExpiresAt,boolean canApprove,boolean canReject,boolean canRevoke){}
 public record Page(long total,int page,int size,List<Request> items){}
 public record Authorization(String requestId,boolean authorizationReady,boolean sessionEnabled,String reason,Scope scope,LocalDateTime expiresAt){}
}
