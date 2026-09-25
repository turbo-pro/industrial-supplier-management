# Supplier resource management

This module manages contractor personnel and supplier-owned vehicles, equipment, and tools within a tenant and optional project boundary.

## Acceptance rules

- Only an active supplier can own a person or asset.
- When a project is selected, it must belong to that supplier and be in `PLANNED`, `ACTIVE`, or `SUSPENDED` state.
- Identity numbers are normalized and stored only as a SHA-256 hash plus a masked display value. The raw number is never persisted or returned.
- Person codes and identity hashes are unique per tenant. Asset codes and non-empty vehicle plate numbers are unique per tenant.
- Person state transitions are `PENDING -> ACTIVE/EXITED`, `ACTIVE -> SUSPENDED/EXITED`, and `SUSPENDED -> ACTIVE/EXITED`. `EXITED` is terminal.
- Asset state transitions are `PENDING -> AVAILABLE/RETIRED`, `AVAILABLE -> IN_USE/MAINTENANCE/RETIRED`, `IN_USE -> AVAILABLE/MAINTENANCE`, and `MAINTENANCE -> AVAILABLE/RETIRED`. `RETIRED` is terminal.
- Suspension, exit, maintenance, and retirement require a reason.
- Optimistic locking rejects stale updates. Tenant and configured organization/project/creator scopes apply to all lists and mutations.

The admin routes are `/resources/persons` and `/resources/assets`. API paths are documented in `contracts/openapi/ism-v1.yaml`.
