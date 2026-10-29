package io.github.turbopro.ism.iam.organization;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Trusted platform provisioning contract. Shares the caller's initialization transaction. */
@Service
public class OrganizationProvisioner {
    private final OrganizationMapper mapper;
    private final OrganizationStructureLock structureLock;
    public OrganizationProvisioner(OrganizationMapper mapper,OrganizationStructureLock structureLock){this.mapper=mapper;this.structureLock=structureLock;}
    public record Structure(long primaryOrganizationId,Long defaultSiteId){}
    @Transactional(propagation=Propagation.MANDATORY)
    public Structure initialize(long tenantId,long administratorId,long root,long site,String mode,String rootName,String siteName){
        if(mode!=null&&!java.util.Set.of("SINGLE_SITE","MULTI_SITE").contains(mode))
            throw new io.github.turbopro.ism.common.api.error.ApiException(io.github.turbopro.ism.common.api.error.CommonErrorCode.VALIDATION_FAILED);
        try(var context=TenantContext.open(tenantId,administratorId)){
            boolean single="SINGLE_SITE".equals(mode);
            mapper.insert(tenantId,root,null,single?"MAIN_SITE":"HEADQUARTERS",single?siteName:rootName,single?"SITE":"HEADQUARTERS",0);
            mapper.insertSelf(tenantId,root);
            if(single)return new Structure(root,root);
            // Null mode is reserved for existing trusted in-process initialization callers.
            if(mode==null)return new Structure(root,null);
            mapper.insert(tenantId,site,root,"MAIN_SITE",siteName,"SITE",0);
            mapper.insertSelf(tenantId,site);mapper.insertAncestors(tenantId,site,root);
            return new Structure(root,site);
        }
    }
    /** Adds a management root without transferring any business or user ownership. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void upgrade(long tenantId,long actorId,long siteId,long headquartersId,String headquartersName){
        try(var context=TenantContext.open(tenantId,actorId)){
            structureLock.acquire();
            var rows=mapper.structureForUpdate(tenantId);
            var byId=rows.stream().collect(java.util.stream.Collectors.toMap(OrganizationModels.OrganizationRow::id,row->row));
            var site=byId.get(siteId);
            if(site==null||site.parentId()!=null||!"SITE".equals(site.organizationType())||!"ACTIVE".equals(site.status()))throw invalidStructure();
            var expected=new java.util.HashSet<OrganizationMapper.ClosureRow>();
            for(var row:rows){
                if(row.id()!=siteId&&!"DEPARTMENT".equals(row.organizationType()))throw invalidStructure();
                var visited=new java.util.HashSet<Long>();
                var ancestor=row;int depth=0;
                while(ancestor!=null){
                    if(!visited.add(ancestor.id()))throw invalidStructure();
                    expected.add(new OrganizationMapper.ClosureRow(ancestor.id(),row.id(),depth++));
                    if(ancestor.parentId()==null){if(ancestor.id()!=siteId)throw invalidStructure();break;}
                    ancestor=byId.get(ancestor.parentId());if(ancestor==null)throw invalidStructure();
                }
            }
            if(!expected.equals(new java.util.HashSet<>(mapper.closureForUpdate(tenantId))))throw invalidStructure();
            try{
                mapper.insert(tenantId,headquartersId,null,"HEADQUARTERS",headquartersName,"HEADQUARTERS",0);
                mapper.insertSelf(tenantId,headquartersId);
                if(mapper.reparentSite(tenantId,siteId,headquartersId,site.version())!=1)throw invalidStructure();
                if(mapper.attachSubtree(tenantId,siteId,headquartersId)!=rows.size())throw invalidStructure();
            }catch(org.springframework.dao.DuplicateKeyException conflict){throw new io.github.turbopro.ism.common.api.error.ApiException(io.github.turbopro.ism.common.api.error.CommonErrorCode.CONFLICT,"总部编码已存在，请先核对组织结构");}
        }
    }
    private io.github.turbopro.ism.common.api.error.ApiException invalidStructure(){return new io.github.turbopro.ism.common.api.error.ApiException(io.github.turbopro.ism.common.api.error.CommonErrorCode.RULE_BLOCKED,"单场站组织结构不完整或已变化，请先核对，系统未自动修复");}

}
