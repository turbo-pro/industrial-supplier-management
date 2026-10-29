package io.github.turbopro.ism.bootstrap;
/** Enables the actual project/supplier subscription in this synthetic tenant; no entitlement bypass. */
abstract class SiteProjectSupport extends SitePreferenceSupport {
    @Override long pending(){long tenant=super.pending();jdbc.update("INSERT INTO plt_package_module(package_version_id,module_id,enabled,quota_json) SELECT t.package_version_id,m.id,1,JSON_OBJECT() FROM plt_tenant t CROSS JOIN plt_module m WHERE t.id=? AND m.module_code IN ('PROJECT','SUPPLIER')",tenant);return tenant;}
}
