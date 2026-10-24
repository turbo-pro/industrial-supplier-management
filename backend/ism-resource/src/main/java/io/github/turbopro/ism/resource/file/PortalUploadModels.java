package io.github.turbopro.ism.resource.file;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class PortalUploadModels {
    private PortalUploadModels(){}
    public record Initialize(@NotBlank @Pattern(regexp="[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}") String requestKey,
        @NotBlank @Size(max=255) String fileName,@NotBlank @Size(max=150) String contentType,
        @Min(8) @Max(10485760) long totalSize,@NotBlank @Pattern(regexp="[a-fA-F0-9]{64}") String sha256){}
    public record Version(@NotNull @Min(0) Integer version){}
    public record View(String id,String fileName,String contentType,long totalSize,String sha256,int chunkSize,int totalChunks,
        List<Integer> uploadedChunks,String status,LocalDateTime expiresAt,int version){}
}
