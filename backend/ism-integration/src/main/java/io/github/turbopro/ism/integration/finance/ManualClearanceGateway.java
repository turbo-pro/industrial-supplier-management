package io.github.turbopro.ism.integration.finance;

import io.github.turbopro.ism.resource.file.FileReferenceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name="ism.integration.finance.mode",havingValue="MANUAL")
public class ManualClearanceGateway implements FinancialClearanceGateway {
    private final ManualClearanceMapper mapper;
    private final FileReferenceService files;
    public ManualClearanceGateway(ManualClearanceMapper mapper,FileReferenceService files){this.mapper=mapper;this.files=files;}
    @Override public Result check(long tenantId,long supplierId) {
        var row=mapper.get(tenantId,supplierId);
        if(row==null||!row.status().equals("APPROVED")||row.reviewedBy()==null
                ||row.reviewedBy()==row.submittedBy()||!files.active(row.evidenceFileId()))
            return new Result(Status.UNKNOWN,0,null,null);
        return new Result(Status.CLEAR,0,row.reviewedAt(),"MANUAL:"+supplierId+":"+row.version()+":"+row.evidenceFileId());
    }
}
