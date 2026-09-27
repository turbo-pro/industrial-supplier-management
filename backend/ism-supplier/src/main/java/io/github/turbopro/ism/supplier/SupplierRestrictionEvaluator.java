package io.github.turbopro.ism.supplier;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Stable domain boundary. Caller must lock and read the supplier in its business transaction first. */
public interface SupplierRestrictionEvaluator {
    enum Action { GENERIC_NEW_BUSINESS, CONTRACT_CREATE, CONTRACT_ACTIVATE, QUALIFICATION_APPLY, PROJECT_CREATE, RESOURCE_ASSIGN, SITE_ENTER, START_WORK, RESUME_WORK }
    enum Decision { ALLOW, WARN, DENY }
    record Hit(String code,String sourceId,Decision decision,String explanation){
        public Hit { Objects.requireNonNull(code);Objects.requireNonNull(sourceId);Objects.requireNonNull(decision);Objects.requireNonNull(explanation); }
    }
    record Evaluation(Decision decision,LocalDate businessDate,List<Hit> hits){
        public Evaluation { Objects.requireNonNull(decision);Objects.requireNonNull(businessDate);hits=List.copyOf(hits); }
    }
    Evaluation evaluateLocked(long supplierId,String supplierStatus,Action action);
}
