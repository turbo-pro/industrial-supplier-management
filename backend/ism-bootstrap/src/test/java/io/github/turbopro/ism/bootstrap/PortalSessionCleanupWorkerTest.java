package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.iam.portal.PortalSessionRetentionService;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortalSessionCleanupWorkerTest {
    @Test void unsafeRetentionOrBatchConfigurationIsRejected(){
        for(int[] settings:List.of(new int[]{29,100},new int[]{3651,100},new int[]{90,0},new int[]{90,501})){
            assertThatThrownBy(()->new PortalSessionRetentionService(null,null,null,null,settings[0],settings[1])).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void disabledWorkerDoesNotDiscoverOrDelete(){
        var service=mock(PortalSessionRetentionService.class);new PortalSessionCleanupWorker(service,false).poll();verifyNoInteractions(service);
    }
    @Test void failedTenantDoesNotLeakContextOrPreventFollowingTenant(){
        var service=mock(PortalSessionRetentionService.class);when(service.candidates(0)).thenReturn(List.of(11L,12L));
        var visited=new ArrayList<Long>();
        when(service.cleanBatch()).thenAnswer(call->{
            var identity=TenantContext.require();assertThat(identity.actorId()).isZero();visited.add(identity.tenantId());
            if(identity.tenantId()==11)throw new IllegalStateException("test failure");return 1;
        });
        new PortalSessionCleanupWorker(service,true).poll();
        assertThat(visited).containsExactly(11L,12L);assertThat(TenantContext.current()).isEmpty();
    }
    @Test void discoveryCursorMovesPastFullPageAndResetsAfterLastPage(){
        var service=mock(PortalSessionRetentionService.class);
        var first=java.util.stream.LongStream.rangeClosed(1,50).boxed().toList();
        when(service.candidates(0)).thenReturn(first);when(service.candidates(50)).thenReturn(List.of(51L));
        var worker=new PortalSessionCleanupWorker(service,true);worker.poll();worker.poll();worker.poll();
        verify(service,times(2)).candidates(0);verify(service).candidates(50);verify(service,times(101)).cleanBatch();
        assertThat(TenantContext.current()).isEmpty();
    }
}
