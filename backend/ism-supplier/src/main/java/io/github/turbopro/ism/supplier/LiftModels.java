package io.github.turbopro.ism.supplier;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class LiftModels {
    private LiftModels(){}
    public enum Decision{APPROVE,REJECT}
    public record Create(@NotBlank @Size(max=2000) String reason,@NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String evidenceFileId){}
    public record Review(@NotNull Decision decision,@NotBlank @Size(max=2000) String comment,@NotNull @PositiveOrZero Integer version){}
    public record Row(long id,long caseId,String reason,long evidenceFileId,String status,long createdBy,
        LocalDateTime createdAt,Long reviewedBy,LocalDateTime reviewedAt,String reviewComment,int version){}
    public record View(String id,String caseId,String reason,String evidenceFileId,String status,String createdBy,
        LocalDateTime createdAt,String reviewedBy,LocalDateTime reviewedAt,String reviewComment,int version){}
    public record Page(long total,int page,int size,List<View> items){}
    public record Readiness(boolean ready,Instant observationEndsAt,List<SupplierExitCheck.Blocker> blockers){}
}
