package io.github.turbopro.ism.supplier;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class ExitModels {
    private ExitModels(){}
    public enum Type{NORMAL,ELIMINATION}
    public enum Decision{APPROVE,REJECT}
    public record Create(@NotNull Type type,@NotBlank @Size(max=2000) String reason,
        @NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String evidenceFileId){}
    public record Review(@NotNull Decision decision,@NotBlank @Size(max=2000) String comment,
        @NotNull @PositiveOrZero Integer version){}
    public record Version(@NotNull @PositiveOrZero Integer version){}
    public record Row(long id,long supplierId,String exitType,String reason,long evidenceFileId,String status,
        long createdBy,LocalDateTime createdAt,Long reviewedBy,LocalDateTime reviewedAt,String reviewComment,int version){}
    public record ItemRow(long id,String checkCode,String checkLabel,String route,long initialCount,long currentCount,LocalDateTime checkedAt){}
    public record Item(String id,String code,String label,String route,long initialCount,long currentCount,LocalDateTime checkedAt){}
    public record EventRow(long id,String action,String comment,long actorId,LocalDateTime createdAt){}
    public record Event(String id,String action,String comment,String actorId,LocalDateTime createdAt){}
    public record ResultRow(long id,long approvedBy,String comment,String completionScope,String accessRecoveryStatus,LocalDateTime effectiveAt){}
    public record Result(String id,String approvedBy,String comment,String completionScope,String accessRecoveryStatus,LocalDateTime effectiveAt){}
    public record View(String id,String supplierId,Type type,String reason,String evidenceFileId,String status,
        String createdBy,LocalDateTime createdAt,String reviewedBy,LocalDateTime reviewedAt,String reviewComment,
        int version,boolean localReady,List<Item> items,List<Event> events,Result result){}
    public record Page(long total,int page,int size,boolean canApply,List<View> items){}
}
