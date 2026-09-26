package io.github.turbopro.ism.integration.finance;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialExitCheckTest {
    private final Instant now = Instant.parse("2026-09-26T00:00:00Z");
    private final FinancialClearanceGateway gateway = mock(FinancialClearanceGateway.class);
    private FinancialExitCheck check(List<FinancialClearanceGateway> gateways) {
        return new FinancialExitCheck(gateways, Duration.ofMinutes(15), Clock.fixed(now, ZoneOffset.UTC));
    }
    @Test void unconfiguredGatewayBlocksExit() {
        assertEquals("FINANCIAL_UNKNOWN", check(List.of()).blockers(99).get(0).code());
    }
    @Test void freshClearanceAllowsExitAndUsesAuthenticatedTenant() {
        when(gateway.check(10,99)).thenReturn(result(FinancialClearanceGateway.Status.CLEAR,0,now));
        try(var tenant=TenantContext.open(10,7)) {
            assertEquals(0,check(List.of(gateway)).blockers(99).get(0).count());
        }
        verify(gateway).check(10,99);
    }
    @Test void unsettledItemsBlockExit() {
        when(gateway.check(10,99)).thenReturn(result(FinancialClearanceGateway.Status.OPEN,3,now));
        try(var tenant=TenantContext.open(10,7)) {
            assertEquals(3,check(List.of(gateway)).blockers(99).get(0).count());
        }
    }
    @Test void missingStaleFutureOrUnverifiableResultsBlockExit() {
        try(var tenant=TenantContext.open(10,7)) {
            for(var value : List.of(result(FinancialClearanceGateway.Status.CLEAR,0,now.minusSeconds(901)),
                    result(FinancialClearanceGateway.Status.CLEAR,0,now.plusSeconds(1)),
                    new FinancialClearanceGateway.Result(FinancialClearanceGateway.Status.CLEAR,0,now,""),
                    new FinancialClearanceGateway.Result(FinancialClearanceGateway.Status.CLEAR,0,null,"ERP-1"),
                    result(FinancialClearanceGateway.Status.UNKNOWN,0,now))) {
                when(gateway.check(10,99)).thenReturn(value);
                assertEquals("FINANCIAL_UNKNOWN",check(List.of(gateway)).blockers(99).get(0).code());
            }
            when(gateway.check(10,99)).thenReturn(null);
            assertEquals(1,check(List.of(gateway)).blockers(99).get(0).count());
        }
    }
    @Test void unavailableGatewayBlocksExit() {
        when(gateway.check(10,99)).thenThrow(new IllegalStateException("ERP unavailable"));
        try(var tenant=TenantContext.open(10,7)) {
            assertEquals(1,check(List.of(gateway)).blockers(99).get(0).count());
        }
    }
    @Test void inconsistentResultsAndAmbiguousConfigurationAreRejected() {
        assertThrows(IllegalArgumentException.class,()->result(FinancialClearanceGateway.Status.CLEAR,1,now));
        assertThrows(IllegalArgumentException.class,()->result(FinancialClearanceGateway.Status.OPEN,0,now));
        assertThrows(IllegalArgumentException.class,()->result(FinancialClearanceGateway.Status.OPEN,-1,now));
        assertThrows(IllegalArgumentException.class,()->check(List.of(gateway,gateway)));
        assertThrows(IllegalArgumentException.class,()->new FinancialExitCheck(List.of(),Duration.ZERO,Clock.systemUTC()));
    }
    private FinancialClearanceGateway.Result result(FinancialClearanceGateway.Status status,long count,Instant at) {
        return new FinancialClearanceGateway.Result(status,count,at,"ERP-1");
    }
}
