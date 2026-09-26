package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AppealServiceTest {
    private final AppealMapper mapper=mock(AppealMapper.class);
    private final BlacklistMapper restrictions=mock(BlacklistMapper.class);
    private final BlacklistService cases=mock(BlacklistService.class);
    private final AppealEvidenceVerifier evidence=mock(AppealEvidenceVerifier.class);
    private final OperationIdGenerator ids=mock(OperationIdGenerator.class);
    private final AppealService service=new AppealService(mapper,restrictions,cases,evidence,ids,mock(AuditService.class));
    private void approvedCase(){
        var visible=mock(BlacklistModels.View.class);when(visible.supplierId()).thenReturn("30");when(cases.get(80)).thenReturn(visible);
        var current=mock(BlacklistModels.Row.class);when(current.status()).thenReturn("APPROVED");when(current.reviewedBy()).thenReturn(8L);
        when(restrictions.currentCase(10,80)).thenReturn(current);when(evidence.available(50)).thenReturn(true);
    }
    private AppealModels.Row row(String status){
        return new AppealModels.Row(90,80,"证据补充",50,status,7,LocalDateTime.now(),null,null,null,0);
    }
    @Test void submissionDoesNotSuspendRestriction(){
        approvedCase();when(ids.nextId()).thenReturn(90L);when(mapper.get(10,80,90)).thenReturn(row("SUBMITTED"));
        try(var tenant=TenantContext.open(10,7)){
            assertEquals("SUBMITTED",service.create(80,new AppealModels.Create("补充证据","50")).status());
        }
        verify(restrictions,never()).revoke(anyLong(),anyLong(),anyString(),anyLong(),anyInt());
        verify(restrictions,never()).review(anyLong(),anyLong(),anyString(),anyString(),anyLong(),anyInt());
    }
    @Test void duplicatePendingAppealConflicts(){
        approvedCase();when(ids.nextId()).thenReturn(90L);
        when(mapper.insert(90,10,80,"证据",50,7)).thenThrow(new DuplicateKeyException("pending"));
        try(var tenant=TenantContext.open(10,7)){
            assertThrows(ApiException.class,()->service.create(80,new AppealModels.Create("证据","50")));
        }
    }
    @Test void missingEvidenceAndUnapprovedCaseBlockSubmission(){
        approvedCase();when(evidence.available(50)).thenReturn(false);
        try(var tenant=TenantContext.open(10,7)){
            assertThrows(ApiException.class,()->service.create(80,new AppealModels.Create("证据","50")));
            when(restrictions.currentCase(10,80).status()).thenReturn("DRAFT");
            assertThrows(ApiException.class,()->service.create(80,new AppealModels.Create("证据","50")));
        }
        verify(mapper,never()).insert(anyLong(),anyLong(),anyLong(),anyString(),anyLong(),anyLong());
    }
    @Test void applicantAndOriginalApproverCannotReview(){
        approvedCase();when(mapper.get(10,80,90)).thenReturn(row("SUBMITTED"));
        for(long actor:new long[]{7,8}){
            try(var tenant=TenantContext.open(10,actor)){
                assertThrows(ApiException.class,()->service.review(80,90,new AppealModels.Review(AppealModels.Decision.ACCEPT,"成立",0)));
            }
        }
        verify(mapper,never()).review(anyLong(),anyLong(),anyLong(),anyString(),anyString(),anyLong(),anyInt());
    }
    @Test void favorableConclusionNeverRevokesRestriction(){
        approvedCase();when(mapper.get(10,80,90)).thenReturn(row("SUBMITTED"),row("ACCEPTED"));
        when(mapper.review(10,80,90,"ACCEPTED","成立",9,0)).thenReturn(1);
        try(var tenant=TenantContext.open(10,9)){
            assertEquals("ACCEPTED",service.review(80,90,new AppealModels.Review(AppealModels.Decision.ACCEPT,"成立",0)).status());
        }
        verify(restrictions,never()).revoke(anyLong(),anyLong(),anyString(),anyLong(),anyInt());
    }
    @Test void terminalAndStaleReviewsAreRejected(){
        approvedCase();
        try(var tenant=TenantContext.open(10,9)){
            when(mapper.get(10,80,90)).thenReturn(row("SUBMITTED"));
            assertThrows(ApiException.class,()->service.review(80,90,new AppealModels.Review(AppealModels.Decision.REJECT,"驳回",1)));
            when(mapper.get(10,80,90)).thenReturn(row("REJECTED"));
            assertThrows(ApiException.class,()->service.review(80,90,new AppealModels.Review(AppealModels.Decision.ACCEPT,"再次复核",0)));
        }
    }
    @Test void deniedCaseNeverLeaksAppealHistory(){
        when(cases.get(80)).thenThrow(new ApiException(CommonErrorCode.NOT_FOUND));
        try(var tenant=TenantContext.open(11,7)){assertThrows(ApiException.class,()->service.list(80,0,20));}
        verifyNoInteractions(mapper);
    }
}
