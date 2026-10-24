package io.github.turbopro.ism.resource.file;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.portal.*;
import io.github.turbopro.ism.operation.*;
import io.github.turbopro.ism.qualification.PortalQualificationUploadReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;

@Service
public class PortalUploadService implements PortalQualificationUploadReference {
    private final PortalUploadMapper ownership;private final FileMapper mapper;private final FileService files;
    private final PortalWriteGuard guard;private final PortalModuleAccessGate modules;private final OperationIdGenerator ids;
    private final AuditService audit;private final List<ObjectStorage> stores;private final FileStorageProperties properties;
    public PortalUploadService(PortalUploadMapper ownership,FileMapper mapper,FileService files,PortalWriteGuard guard,PortalModuleAccessGate modules,OperationIdGenerator ids,AuditService audit,List<ObjectStorage> stores,FileStorageProperties properties){this.ownership=ownership;this.mapper=mapper;this.files=files;this.guard=guard;this.modules=modules;this.ids=ids;this.audit=audit;this.stores=stores;this.properties=properties;}
    @Transactional public PortalUploadModels.View initialize(PortalUploadModels.Initialize command){
        var i=identity();validate(command);Long existing=ownership.byKey(i.tenantId(),i.supplierId(),i.accountId(),command.requestKey());
        if(existing!=null){var row=lock(existing);if(!row.fileName().equals(command.fileName())||!row.contentType().equals(command.contentType())||row.totalSize()!=command.totalSize()||!row.fileSha256().equalsIgnoreCase(command.sha256()))throw new ApiException(CommonErrorCode.CONFLICT,"上传标识已用于其他文件");return view(row);}
        if(ownership.recent(i.tenantId(),i.supplierId(),i.accountId())>=10||ownership.recent(i.tenantId(),i.supplierId(),0)>=50)throw new ApiException(CommonErrorCode.RATE_LIMITED,"24小时上传额度已用完，请稍后再试");
        long id=ids.nextId();int chunkSize=1024*1024;
        mapper.insertUpload(i.tenantId(),id,i.accountId(),command.fileName(),command.contentType(),command.totalSize(),command.sha256().toLowerCase(),chunkSize,(int)((command.totalSize()+chunkSize-1)/chunkSize),"UPLOADING",null,now().plusHours(24));
        ownership.insert(id,i.tenantId(),i.supplierId(),i.accountId(),command.requestKey());record("PORTAL_UPLOAD_INITIALIZE",id);
        return view(lock(id));
    }
    @Transactional public PortalUploadModels.View get(long id){identity();return view(lock(id));}
    @Transactional public PortalUploadModels.View chunk(long id,int index,String hash,byte[] bytes){
        identity();var row=lock(id);available(row);
        if(index==0&&!signature(row.contentType(),bytes))throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"文件头与声明格式不符");
        files.putChunk(id,index,hash,bytes);return view(lock(id));
    }
    @Transactional public PortalUploadModels.View complete(long id,int version){
        var i=identity();var row=lock(id);available(row);
        if(row.status().equals("COMPLETED")){requireActive(row);return view(row);}
        if(row.version()!=version)throw new ApiException(CommonErrorCode.CONFLICT);
        if(mapper.uploadedChunks(i.tenantId(),id).size()!=row.totalChunks())throw new ApiException(FileErrorCode.UPLOAD_INCOMPLETE);
        ObjectStorage store=store();
        // Always prove the complete content, even when another employee/enterprise uploaded the same hash.
        try{
            var object=store.prepareUpload(i.tenantId(),id,row.totalChunks(),row.fileSha256(),row.totalSize());
            var existing=mapper.fileByHash(i.tenantId(),object.sha256(),object.size());long fileId;
            if(existing!=null)fileId=existing.id();
            else{fileId=ids.nextId();try{mapper.insertFile(i.tenantId(),fileId,i.accountId(),row.fileName(),row.contentType(),object.size(),object.sha256(),store.provider(),object.objectKey());}catch(org.springframework.dao.DuplicateKeyException duplicate){var concurrent=mapper.fileByHashCurrent(i.tenantId(),object.sha256(),object.size());if(concurrent==null)throw duplicate;fileId=concurrent.id();}}
            if(mapper.claimCompletion(i.tenantId(),id,version)!=1)throw new ApiException(CommonErrorCode.CONFLICT);
            mapper.completeUpload(i.tenantId(),id,fileId);record("PORTAL_UPLOAD_COMPLETE",id);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){try{store.discardUpload(i.tenantId(),id);}catch(RuntimeException ignored){/* Retry/retention can remove orphan chunks; never misreport committed completion. */}}});
            return view(lock(id));
        }catch(FileContentMismatchException e){throw new ApiException(FileErrorCode.INVALID_CHUNK,"完整原件摘要不符，请重新选择正确文件上传");}catch(FileStorageException e){throw new ApiException(FileErrorCode.STORAGE_UNAVAILABLE);}
    }
    @Override @Transactional(propagation=Propagation.MANDATORY) public Ready requireReady(long id){
        identity();var row=lock(id);available(row);if(!row.status().equals("COMPLETED"))throw new ApiException(CommonErrorCode.RULE_BLOCKED,"请先完成原件上传");requireActive(row);return new Ready(row.fileId(),row.fileName());
    }
    private PortalIdentityContext.Identity identity(){guard.lockCurrent();modules.requireAvailable("QUALIFICATION");return PortalIdentityContext.require();}
    private FileModels.UploadRow lock(long id){var i=PortalIdentityContext.require();var row=ownership.lock(i.tenantId(),i.supplierId(),i.accountId(),id);if(row==null)throw new ApiException(CommonErrorCode.NOT_FOUND);return row;}
    private void available(FileModels.UploadRow row){if(!row.expiresAt().isAfter(now()))throw new ApiException(FileErrorCode.UPLOAD_EXPIRED);}
    private void requireActive(FileModels.UploadRow row){if(row.fileId()==null||mapper.file(PortalIdentityContext.require().tenantId(),row.fileId())==null)throw new ApiException(CommonErrorCode.NOT_FOUND,"上传原件已不可用");}
    private PortalUploadModels.View view(FileModels.UploadRow row){return new PortalUploadModels.View(Long.toString(row.id()),row.fileName(),row.contentType(),row.totalSize(),row.fileSha256(),row.chunkSize(),row.totalChunks(),mapper.uploadedChunks(PortalIdentityContext.require().tenantId(),row.id()),row.status(),row.expiresAt(),row.version());}
    private ObjectStorage store(){return stores.stream().filter(s->s.provider().equalsIgnoreCase(properties.getProvider())).findFirst().orElseThrow(()->new ApiException(FileErrorCode.STORAGE_UNAVAILABLE));}
    private void validate(PortalUploadModels.Initialize c){
        String extension=Map.of("application/pdf",".pdf","image/png",".png","image/jpeg",".jpg").get(c.contentType());
        String name=c.fileName().toLowerCase(Locale.ROOT);
        if(extension==null||!(name.endsWith(extension)||c.contentType().equals("image/jpeg")&&name.endsWith(".jpeg"))||c.fileName().chars().anyMatch(ch->ch<32||ch=='/'||ch=='\\')||c.totalSize()<8||c.totalSize()>10*1024*1024)throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"仅支持10MB以内PDF、PNG、JPEG原件");
        String configured=mapper.tenantSetting(PortalIdentityContext.require().tenantId(),"upload.maxFileSizeMb");
        long limit=properties.getMaxFileSize();if(configured!=null)try{limit=Math.multiplyExact(Long.parseLong(configured),1024L*1024L);}catch(ArithmeticException|NumberFormatException ignored){}
        if(c.totalSize()>limit)throw new ApiException(FileErrorCode.FILE_TOO_LARGE);
    }
    private boolean signature(String type,byte[] bytes){
        if(bytes.length<8)return false;
        return switch(type){case "application/pdf" -> bytes[0]=='%'&&bytes[1]=='P'&&bytes[2]=='D'&&bytes[3]=='F'&&bytes[4]=='-';case "image/png" -> Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10});case "image/jpeg" -> bytes[0]==(byte)255&&bytes[1]==(byte)216&&bytes[2]==(byte)255;default -> false;};
    }
    private void record(String action,long id){audit.append(new AuditService.AuditCommand(action,"PORTAL_UPLOAD",id,null,Map.of(),Map.of("supplierId",PortalIdentityContext.require().supplierId()),null,null));}
    private LocalDateTime now(){return LocalDateTime.now(ZoneOffset.UTC);}
}
