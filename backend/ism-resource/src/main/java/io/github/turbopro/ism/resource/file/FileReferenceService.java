package io.github.turbopro.ism.resource.file;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;

@Service
public class FileReferenceService {
    private final FileMapper mapper;
    public FileReferenceService(FileMapper mapper) { this.mapper = mapper; }
    public boolean activeForQualification(long fileId){return mapper.activeForQualification(TenantContext.require().tenantId(),fileId)!=null;}
    public boolean active(long fileId) { return mapper.file(TenantContext.require().tenantId(), fileId) != null; }
}
