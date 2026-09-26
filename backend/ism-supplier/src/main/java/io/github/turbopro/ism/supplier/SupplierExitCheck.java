package io.github.turbopro.ism.supplier;

import java.util.List;

/** Business modules expose exit facts without sharing their tables or mappers. */
public interface SupplierExitCheck {
    List<Blocker> blockers(long supplierId);
    record Blocker(String code, String label, long count, String route) {}
}
