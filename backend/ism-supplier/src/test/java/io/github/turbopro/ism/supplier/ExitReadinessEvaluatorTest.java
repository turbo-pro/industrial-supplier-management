package io.github.turbopro.ism.supplier;

import io.github.turbopro.ism.common.api.error.ApiException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExitReadinessEvaluatorTest {
    static List<SupplierExitCheck.Blocker> clear(){
        return List.of("OPEN_CONTRACT","OPEN_PROJECT","OPEN_PERSON","OPEN_ASSET","OPEN_SAFETY",
            "OPEN_ATTENDANCE","OPEN_QUALITY","OPEN_IMPROVEMENT","OPEN_FINANCIAL").stream()
            .map(code->new SupplierExitCheck.Blocker(code,code,0,"/suppliers/master")).toList();
    }
    @Test void allLocalCapabilitiesAreRequired(){
        assertTrue(ExitReadinessEvaluator.evaluate(List.of(id->clear()),99).ready());
        assertFalse(ExitReadinessEvaluator.evaluate(List.of(),99).ready());
        assertFalse(ExitReadinessEvaluator.evaluate(List.of(id->List.of(clear().get(0))),99).ready());
    }
    @Test void invalidOrDuplicateFactsCannotClearSupplier(){
        assertThrows(ApiException.class,()->ExitReadinessEvaluator.evaluate(List.of(id->null),99));
        assertThrows(ApiException.class,()->ExitReadinessEvaluator.evaluate(List.of(id->clear(),id->clear()),99));
        assertThrows(ApiException.class,()->ExitReadinessEvaluator.evaluate(List.of(id->List.of(new SupplierExitCheck.Blocker("OPEN_CONTRACT","合同",-1,"/projects/contracts"))),99));
        assertThrows(ApiException.class,()->ExitReadinessEvaluator.evaluate(List.of(id->List.of(new SupplierExitCheck.Blocker("FINANCIAL_UNKNOWN","未知财务",0,"/suppliers/master"))),99));
    }
}
