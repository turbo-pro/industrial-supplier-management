package io.github.turbopro.ism.supplier;

import java.time.LocalDateTime;

public final class ExitArchiveModels {
    private ExitArchiveModels() {}
    public record Row(long id,long applicationId,long supplierId,long resultId,long evidenceFileId,
        int schemaVersion,String digestSha256,int itemCount,long entityCount,int eventCount,long sealedBy,LocalDateTime sealedAt) {}
    public record View(String id,String applicationId,String supplierId,String resultId,String evidenceFileId,
        int schemaVersion,String digestSha256,int itemCount,long entityCount,int eventCount,String sealedBy,
        LocalDateTime sealedAt,boolean integrityVerified,String scope,String externalAccessStatus) {}
}
