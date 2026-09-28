package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import io.github.turbopro.ism.operation.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** Seals retained local database facts; it does not copy evidence files or verify external access recovery. */
@Service
public class ExitArchiveService {
    private final ExitArchiveMapper archives;
    private final ExitMapper exits;
    private final SupplierService scope;
    private final OperationIdGenerator ids;
    private final AuditService audit;

    public ExitArchiveService(ExitArchiveMapper archives,ExitMapper exits,SupplierService scope,OperationIdGenerator ids,AuditService audit){
        this.archives=archives;this.exits=exits;this.scope=scope;this.ids=ids;this.audit=audit;
    }

    /** Called inside the approval transaction, after the result and closing event are persisted. */
    public void seal(long supplierId,long applicationId){
        var identity=TenantContext.require();
        var application=exits.get(identity.tenantId(),supplierId,applicationId);
        var result=exits.findResult(identity.tenantId(),applicationId);
        if(application==null||!"BUSINESS_CLOSED".equals(application.status())||result==null
                ||!"LOCAL_BUSINESS".equals(result.completionScope())||!"NOT_VERIFIED".equals(result.accessRecoveryStatus()))
            throw new ApiException(CommonErrorCode.CONFLICT,"退出本地结果尚未完成，不能封存");
        var facts=fingerprint(identity.tenantId(),application,result);
        if(archives.insert(ids.nextId(),identity.tenantId(),applicationId,supplierId,result.id(),application.evidenceFileId(),
                facts.digest(),facts.itemCount(),facts.entityCount(),facts.eventCount(),identity.actorId())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT,"退出封存失败");
        audit.append(new AuditService.AuditCommand("SUPPLIER_EXIT_ARCHIVE_SEAL","SUPPLIER_EXIT",applicationId,null,Map.of(),
            Map.of("supplierId",supplierId,"itemCount",facts.itemCount(),"entityCount",facts.entityCount(),"eventCount",facts.eventCount()),null,null));
    }

    @Transactional
    public ExitArchiveModels.View get(long supplierId,long applicationId){
        scope.get(supplierId);
        long tenantId=TenantContext.require().tenantId();
        var archive=archives.find(tenantId,supplierId,applicationId);
        if(archive==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        var application=exits.archiveApplication(tenantId,supplierId,applicationId);
        var result=exits.findResult(tenantId,applicationId);
        boolean intact=application!=null&&result!=null&&"BUSINESS_CLOSED".equals(application.status())
            &&archive.resultId()==result.id()&&archive.evidenceFileId()==application.evidenceFileId();
        if(intact){var current=fingerprint(tenantId,application,result);
            intact=archive.digestSha256().equals(current.digest())&&archive.itemCount()==current.itemCount()
                &&archive.entityCount()==current.entityCount()&&archive.eventCount()==current.eventCount();}
        audit.append(new AuditService.AuditCommand("SUPPLIER_EXIT_ARCHIVE_VIEW","SUPPLIER_EXIT",applicationId,null,Map.of(),
            Map.of("supplierId",supplierId,"integrityVerified",intact),null,null));
        return new ExitArchiveModels.View(Long.toString(archive.id()),Long.toString(applicationId),Long.toString(supplierId),
            Long.toString(archive.resultId()),Long.toString(archive.evidenceFileId()),archive.schemaVersion(),archive.digestSha256(),
            archive.itemCount(),archive.entityCount(),archive.eventCount(),Long.toString(archive.sealedBy()),archive.sealedAt(),
            intact,"LOCAL_RECORD_METADATA","NOT_VERIFIED");
    }

    private Fingerprint fingerprint(long tenantId,ExitModels.Row application,ExitModels.ResultRow result){
        MessageDigest digest;
        try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        add(digest,"ISM_EXIT_ARCHIVE_V1",application.id(),application.supplierId(),application.exitType(),application.reason(),
            application.evidenceFileId(),application.status(),application.createdBy(),application.createdAt(),
            application.reviewedBy(),application.reviewedAt(),application.reviewComment(),application.version());
        add(digest,"RESULT",result.id(),result.approvedBy(),result.comment(),result.completionScope(),result.accessRecoveryStatus(),result.effectiveAt());
        var items=exits.items(tenantId,application.id());
        for(var item:items)add(digest,"ITEM",item.id(),item.checkCode(),item.checkLabel(),item.route(),item.initialCount(),item.currentCount(),item.checkedAt());
        long count=exits.entityCount(tenantId,application.id());
        for(long offset=0;offset<count;offset+=100){
            var batch=exits.entities(tenantId,application.id(),Math.toIntExact(offset),100);
            if(batch.isEmpty())throw new ApiException(CommonErrorCode.CONFLICT,"退出事项数量与明细不一致");
            for(var entity:batch)add(digest,"ENTITY",entity.id(),entity.checkCode(),entity.sourceId(),entity.route(),entity.state(),
                entity.assigneeId(),entity.note(),entity.assignedBy(),entity.assignedAt(),entity.checkedAt(),entity.clearedAt(),
                entity.version(),entity.dueDate(),entity.lastRemindedAt());
        }
        var events=exits.events(tenantId,application.id());
        for(var event:events)add(digest,"EVENT",event.id(),event.action(),event.comment(),event.actorId(),event.createdAt());
        return new Fingerprint(HexFormat.of().formatHex(digest.digest()),items.size(),count,events.size());
    }

    private static void add(MessageDigest digest,Object... fields){
        for(Object field:fields){
            if(field==null){digest.update(ByteBuffer.allocate(4).putInt(-1).array());continue;}
            byte[] bytes=String.valueOf(field).getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);
        }
    }
    private record Fingerprint(String digest,int itemCount,long entityCount,int eventCount) {}
}
