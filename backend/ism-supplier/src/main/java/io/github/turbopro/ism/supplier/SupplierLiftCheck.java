package io.github.turbopro.ism.supplier;

import java.util.List;

/** Modules expose mandatory correction facts for independent restriction lifting. */
public interface SupplierLiftCheck {
    List<SupplierExitCheck.Blocker> blockers(long supplierId);
}
