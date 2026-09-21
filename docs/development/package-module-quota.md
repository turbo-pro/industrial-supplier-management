# Package, module and quota contract

Package plans are global control-plane data. A package has editable metadata and immutable versions. Each version contains module grants and non-negative quota limits. Publishing changes a version from `DRAFT` to `PUBLISHED`; published rows and grants have no update path.

Tenant effective capability is the intersection of the published package version, future tenant switches, role permission and business state. `PackagePlanService.requireModule` and `requireQuota` are the server-side gates for business APIs; hiding a menu is never sufficient authorization.

Quota codes initially cover users, suppliers, storage, single-file size, concurrent tasks and API calls. A limit of zero means no creation capacity, not unlimited. Reads, exports, cleanup and exits remain available when a creation quota is full. Downgrade preview reports removed modules, lower limits, current usage and remediation advice; assignment is blocked while usage exceeds the target limit.

Console writes require `Idempotency-Key`. Package publishing and subscription assignment append audit events in platform scope (`tenant_id=0`). Platform scope is an operation partition only and does not create a tenant business identity.
