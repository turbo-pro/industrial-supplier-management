# Package, module and quota contract

Package plans are global control-plane data. A package has editable metadata and immutable versions. Each version contains module grants and non-negative quota limits. Publishing changes a version from `DRAFT` to `PUBLISHED`; published rows and grants have no update path.

Tenant effective capability is the intersection of the published package version, future tenant switches, role permission and business state. `PackagePlanService.requireModule` and `requireQuota` are the server-side gates for business APIs; hiding a menu is never sufficient authorization.

Quota codes initially cover users, suppliers, storage, single-file size, concurrent tasks and API calls. A limit of zero means no creation capacity, not unlimited. Reads, exports, cleanup and exits remain available when a creation quota is full. Downgrade preview reports removed modules, lower limits, current usage and remediation advice; assignment is blocked while usage exceeds the target limit.

Console writes require `Idempotency-Key`. Package publishing and subscription assignment append audit events in platform scope (`tenant_id=0`). Platform scope is an operation partition only and does not create a tenant business identity.

Menu catalog options, role menu writes and current navigation use a shared entitlement rule against the tenant's assigned published version and enabled active modules. SYSTEM, COMMON, MESSAGE and WORKFLOW are fixed shared capabilities. V63 separates shared file/task/print/search menus from the business RESOURCE module. Downgrade preserves stored role menu grants but hides unavailable navigation; replacing grants with unavailable menus fails atomically with 422. See [menu acceptance](b1-package-menu-entitlement-review.md) for the initial menu increment and the V64 extension below.

V64 adds explicit action/field permission ownership and applies the same allowed module set to catalog options, role writes and per-request authorization snapshots. Unmapped permissions fail closed. IAM obtains modules through the public `TenantModuleEntitlements` contract implemented by the platform, rather than querying package tables. Qualification and restriction navigation children are classified independently under a shared supplier container. Data-scope catalog mapping, complete module dependencies, subscription scheduling and expiry remain open; see [permission acceptance](b1-package-permission-entitlement-review.md).
