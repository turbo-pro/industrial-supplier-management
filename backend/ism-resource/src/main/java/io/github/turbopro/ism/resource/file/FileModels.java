package io.github.turbopro.ism.resource.file;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

public final class FileModels {
    private FileModels(){}
    public record InitializeUpload(@NotBlank @Size(max=255) String fileName,
        @NotBlank @Size(max=150) String contentType,@Positive long totalSize,
        @NotBlank @Pattern(regexp="[a-fA-F0-9]{64}") String sha256){}
    public record UploadView(String id,String fileName,String contentType,long totalSize,String sha256,
        int chunkSize,int totalChunks,List<Integer> uploadedChunks,String status,String fileId,LocalDateTime expiresAt,int version){}
    public record FileView(String id,String originalName,String contentType,long size,String sha256,String provider,String status,LocalDateTime createdAt){}
    public record UploadRow(long id,long uploaderId,String fileName,String contentType,long totalSize,String fileSha256,int chunkSize,int totalChunks,String status,Long fileId,LocalDateTime expiresAt,int version){}
    public record FileRow(long id,long ownerId,String originalName,String contentType,long fileSize,String fileSha256,String storageProvider,String objectKey,String status,LocalDateTime createdAt){}
}
