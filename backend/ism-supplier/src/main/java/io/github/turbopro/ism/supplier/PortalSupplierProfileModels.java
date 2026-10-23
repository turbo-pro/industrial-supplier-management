package io.github.turbopro.ism.supplier;

import java.time.LocalDate;
import java.util.List;

public final class PortalSupplierProfileModels {
    private PortalSupplierProfileModels() {}
    /** Explicit portal field allowlist; never serialize the internal supplier entity. */
    public record Profile(String id,String code,String name,String shortName,String unifiedSocialCreditCode,
        SupplierModels.Type type,String industry,String countryCode,String province,String city,String address,
        LocalDate establishedDate,String website,SupplierModels.Status status,int version) {}
    public record Category(String code,String name) {}
    public record View(Profile supplier,List<Category> purchaseCategories) {
        public View { purchaseCategories=List.copyOf(purchaseCategories); }
    }
}
