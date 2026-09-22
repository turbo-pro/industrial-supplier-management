package io.github.turbopro.ism.resource.file;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.OperationIdGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

@Service
public class FileService {
    private final FileMapper mapper;private final OperationIdGenerator ids;private final FileStorageProperties properties;private final List<ObjectStorage> stores;
    public FileService(FileMapper mapper,OperationIdGenerator ids,FileStorageProperties properties,List<ObjectStorage> stores){this.mapper=mapper;this.ids=ids;this.properties=properties;this.stores=stores;}
    @Transactional
    public FileModels.UploadView initialize(FileModels.InitializeUpload command){
        try{MediaType.parseMediaType(command.contentType());}catch(Exception e){throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"文件内容类型无效");}
        long tenantId=tenant();if(command.totalSize()>maximumSize(tenantId))throw new ApiException(FileErrorCode.FILE_TOO_LARGE);
        long actorId=TenantContext.require().actorId();String hash=command.sha256().toLowerCase();var existing=mapper.fileByHash(tenantId,hash,command.totalSize());
        int chunks=(int)((command.totalSize()+properties.getChunkSize()-1)/properties.getChunkSize());long id=ids.nextId();
        mapper.insertUpload(tenantId,id,actorId,safeName(command.fileName()),command.contentType(),command.totalSize(),hash,properties.getChunkSize(),chunks,existing==null?"UPLOADING":"COMPLETED",existing==null?null:existing.id(),now().plus(properties.getUploadTtl()));
        return upload(id);
    }
    public FileModels.UploadView upload(long id){long tenantId=tenant();var row=requireUpload(tenantId,id);return view(tenantId,row);}
    @Transactional
    public FileModels.UploadView putChunk(long id,int index,String expectedHash,byte[] content){
        long tenantId=tenant();var upload=requireWritable(tenantId,id);if(index<0||index>=upload.totalChunks())throw new ApiException(FileErrorCode.INVALID_CHUNK);
        int expectedSize=index==upload.totalChunks()-1?(int)(upload.totalSize()-(long)upload.chunkSize()*(upload.totalChunks()-1)):upload.chunkSize();
        String actual=sha256(content);if(content.length!=expectedSize||expectedHash==null||!actual.equalsIgnoreCase(expectedHash))throw new ApiException(FileErrorCode.INVALID_CHUNK);
        String recorded=mapper.chunkHash(tenantId,id,index);if(recorded!=null){if(!recorded.equals(actual))throw new ApiException(CommonErrorCode.CONFLICT);return view(tenantId,upload);}
        try{storage().writeChunk(tenantId,id,index,content);mapper.insertChunk(tenantId,ids.nextId(),id,index,content.length,actual);}catch(DuplicateKeyException exception){String concurrent=mapper.chunkHash(tenantId,id,index);if(!actual.equals(concurrent))throw new ApiException(CommonErrorCode.CONFLICT);}catch(FileStorageException exception){throw new ApiException(FileErrorCode.STORAGE_UNAVAILABLE);}
        return upload(id);
    }
    @Transactional
    public FileModels.FileView complete(long id,int version){
        long tenantId=tenant();var upload=requireWritable(tenantId,id);if(mapper.uploadedChunks(tenantId,id).size()!=upload.totalChunks())throw new ApiException(FileErrorCode.UPLOAD_INCOMPLETE);
        if(mapper.claimCompletion(tenantId,id,version)!=1)throw new ApiException(CommonErrorCode.CONFLICT);
        var duplicate=mapper.fileByHash(tenantId,upload.fileSha256(),upload.totalSize());if(duplicate!=null){mapper.completeUpload(tenantId,id,duplicate.id());storage().discardUpload(tenantId,id);return fileView(duplicate);}
        try{var stored=storage().complete(tenantId,id,upload.totalChunks(),upload.fileSha256(),upload.totalSize());long fileId=ids.nextId();mapper.insertFile(tenantId,fileId,upload.uploaderId(),upload.fileName(),upload.contentType(),stored.size(),stored.sha256(),storage().provider(),stored.objectKey());mapper.completeUpload(tenantId,id,fileId);return file(fileId);}catch(FileStorageException exception){throw new ApiException(FileErrorCode.STORAGE_UNAVAILABLE);}
    }
    public List<FileModels.FileView> files(){return mapper.files(tenant()).stream().map(this::fileView).toList();}
    public FileModels.FileView file(long id){return fileView(requireFile(tenant(),id));}
    public Download download(long id){var row=requireFile(tenant(),id);try{return new Download(fileView(row),storage(row.storageProvider()).open(row.objectKey()));}catch(FileStorageException exception){throw new ApiException(FileErrorCode.STORAGE_UNAVAILABLE);}}
    @Transactional public void delete(long id){if(mapper.deleteFile(tenant(),id)!=1)throw new ApiException(CommonErrorCode.NOT_FOUND);}
    private FileModels.UploadRow requireUpload(long tenantId,long id){var row=mapper.upload(tenantId,id);if(row==null)throw new ApiException(CommonErrorCode.NOT_FOUND);return row;}
    private FileModels.UploadRow requireWritable(long tenantId,long id){var row=requireUpload(tenantId,id);if(row.expiresAt().isBefore(now()))throw new ApiException(FileErrorCode.UPLOAD_EXPIRED);if(!row.status().equals("UPLOADING"))throw new ApiException(CommonErrorCode.CONFLICT);return row;}
    private FileModels.FileRow requireFile(long tenantId,long id){var row=mapper.file(tenantId,id);if(row==null)throw new ApiException(CommonErrorCode.NOT_FOUND);return row;}
    private FileModels.UploadView view(long tenantId,FileModels.UploadRow row){return new FileModels.UploadView(Long.toString(row.id()),row.fileName(),row.contentType(),row.totalSize(),row.fileSha256(),row.chunkSize(),row.totalChunks(),mapper.uploadedChunks(tenantId,row.id()),row.status(),row.fileId()==null?null:Long.toString(row.fileId()),row.expiresAt(),row.version());}
    private FileModels.FileView fileView(FileModels.FileRow row){return new FileModels.FileView(Long.toString(row.id()),row.originalName(),row.contentType(),row.fileSize(),row.fileSha256(),row.storageProvider(),row.status(),row.createdAt());}
    private ObjectStorage storage(){return storage(properties.getProvider());}private ObjectStorage storage(String provider){return stores.stream().filter(item->item.provider().equalsIgnoreCase(provider)).findFirst().orElseThrow(()->new ApiException(FileErrorCode.STORAGE_UNAVAILABLE));}
    private long maximumSize(long tenantId){String configured=mapper.tenantSetting(tenantId,"upload.maxFileSizeMb");if(configured==null)return properties.getMaxFileSize();try{return Math.multiplyExact(Long.parseLong(configured),1024L*1024L);}catch(Exception e){return properties.getMaxFileSize();}}
    private long tenant(){return TenantContext.require().tenantId();}private LocalDateTime now(){return LocalDateTime.now(ZoneOffset.UTC);}
    private String safeName(String value){String name=value.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).trim();if(name.isBlank()||name.equals(".")||name.equals(".."))throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"文件名无效");return name;}
    private String sha256(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception e){throw new IllegalStateException(e);}}
    public record Download(FileModels.FileView metadata,InputStream stream){}
}
