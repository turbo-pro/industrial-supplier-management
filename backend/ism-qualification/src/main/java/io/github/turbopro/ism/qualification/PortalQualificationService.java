package io.github.turbopro.ism.qualification;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.portal.*;
import io.github.turbopro.ism.operation.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.util.Map;

@Service
public class PortalQualificationService {
    private final PortalQualificationMapper mapper;
    private final PortalModuleAccessGate modules;
    private final PortalWriteGuard guard;
    private final QualificationFileContent files;
    private final AuditService audit;
    public PortalQualificationService(PortalQualificationMapper mapper,PortalModuleAccessGate modules,PortalWriteGuard guard,QualificationFileContent files,AuditService audit){this.mapper=mapper;this.modules=modules;this.guard=guard;this.files=files;this.audit=audit;}
    @Transactional(readOnly=true)
    public PortalQualificationModels.Page list(String keyword,String status,int page,int size){
        var identity=identity();
        if(page<0||page>10000||size<1||size>100||keyword==null||keyword.length()>100)throw invalid();
        String state=null;
        if(status!=null&&!status.isBlank())try{state=PortalQualificationModels.Status.valueOf(status).name();}catch(IllegalArgumentException e){throw invalid();}
        String like=keyword.isBlank()?null:"%"+keyword.trim().replace("=","==").replace("%","=%").replace("_","=_")+"%";
        return new PortalQualificationModels.Page(mapper.count(identity.tenantId(),identity.supplierId(),like,state),page,size,
            mapper.list(identity.tenantId(),identity.supplierId(),like,state,Math.multiplyExact(page,size),size));
    }
    @Transactional(readOnly=true)
    public PortalQualificationModels.Certificate detail(long id){
        var identity=identity();var certificate=mapper.find(identity.tenantId(),identity.supplierId(),id);
        if(certificate==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        return certificate;
    }
    @Transactional
    public QualificationFileContent.Download download(long id){
        // Revalidate revocation after the HTTP filter, serialize against exit/account invalidation.
        guard.lockCurrent();var identity=identity();
        Long fileId=mapper.fileId(identity.tenantId(),identity.supplierId(),id);
        if(fileId==null)throw new ApiException(CommonErrorCode.NOT_FOUND);
        var result=files.open(fileId);
        try{
            // Means authorization/open succeeded, not proof the remote client received every byte.
            audit.append(new AuditService.AuditCommand("PORTAL_QUALIFICATION_DOWNLOAD_OPEN","QUALIFICATION",id,null,Map.of(),Map.of("supplierId",identity.supplierId()),null,null));
            String ownName=mapper.portalFileName(identity.tenantId(),identity.supplierId(),id);
            return ownName==null?result:new QualificationFileContent.Download(ownName,result.size(),result.stream());
        }catch(RuntimeException e){try{result.stream().close();}catch(IOException close){e.addSuppressed(close);}throw e;}
    }
    private PortalIdentityContext.Identity identity(){var identity=PortalIdentityContext.require();modules.requireAvailable("QUALIFICATION");return identity;}
    private ApiException invalid(){return new ApiException(CommonErrorCode.VALIDATION_FAILED,"证照筛选或分页参数无效");}
}
