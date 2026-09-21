# Console authentication and platform authorization

The platform Console is a control plane. It uses independent `plt_user`, role, permission and refresh-token tables, the JWT audience `ism-console`, and the `platform:*` permission namespace. Tenant administration uses `iam_user`, audience `ism-admin`, and a tenant context. Tokens are deliberately not interchangeable.

Console routes begin with `/api/console/`. Public authentication routes are login and refresh; all other routes require a live platform token family. Password change and logout remain available while forced password change is active. Refresh tokens rotate, and replay revokes the whole token family.

Platform permissions are loaded on every authenticated request and enforced server-side through `@RequiresPermission`. The frontend may hide unavailable actions for usability, but it is not an authorization boundary. A platform role never grants tenant identity or direct access to supplier, contract, project, person, attachment or other tenant business content.

No default platform user or password is stored in migrations. Installation and Demo provisioning must create the first operator through a dedicated bootstrap mechanism added by the deployment task.
