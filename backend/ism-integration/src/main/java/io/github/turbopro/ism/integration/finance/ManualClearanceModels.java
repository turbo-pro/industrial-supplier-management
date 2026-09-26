package io.github.turbopro.ism.integration.finance;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class ManualClearanceModels {
    private ManualClearanceModels() {}
    public record Submit(@NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String evidenceFileId,
                         @NotBlank @Size(max=2000) String statement,
                         @NotNull @PositiveOrZero Integer version) {}
    public record Review(@NotNull Decision decision,@NotBlank @Size(max=1000) String comment,
                         @NotNull @PositiveOrZero Integer version) {}
    public enum Decision { APPROVE, REJECT, REVOKE }
    public record Row(String status,long evidenceFileId,String statement,long submittedBy,long submittedAtMillis,
                      Long reviewedBy,Long reviewedAtMillis,String reviewComment,int version) {
        public Instant submittedAt(){return Instant.ofEpochMilli(submittedAtMillis);}
        public Instant reviewedAt(){return reviewedAtMillis==null?null:Instant.ofEpochMilli(reviewedAtMillis);}
    }
    public record Record(String status,String evidenceFileId,String statement,String submittedBy,Instant submittedAt,
                         String reviewedBy,Instant reviewedAt,String reviewComment,int version) {}
    public record View(boolean manualEnabled,Record record) {}
}
