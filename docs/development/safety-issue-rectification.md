# Safety issue rectification

The safety module closes the industrial-site issue lifecycle: an authorized user records an issue against an active project, the responsible party submits evidence, and a verifier passes or rejects the result.

## Acceptance rules

- Issue numbers are unique per tenant; project and supplier ownership are derived server-side.
- Discovery cannot be in the future, and the deadline cannot precede discovery.
- `OPEN` accepts rectification evidence and becomes `PENDING_REVIEW`.
- Verification `PASS` closes the issue; `REJECT` returns it to `OPEN` for another cycle.
- Closed issues are terminal. Every mutation uses optimistic locking and audit records.
- Non-closed issues past their deadline are returned with `overdue=true`.
- Tenant, organization, project, and creator data scopes apply to lists and mutations.

Admin route: `/safety/issues`. API contract: `contracts/openapi/ism-v1.yaml`.
