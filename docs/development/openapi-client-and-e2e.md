# OpenAPI client and frontend smoke test

`contracts/openapi/ism-v1.yaml` is the versioned HTTP contract. Backend DTO or endpoint changes must update this contract in the same commit. Frontend applications import `@ism/api-client`; they must not duplicate transport DTOs by hand.

## Local commands

```bash
pnpm install
pnpm generate:api
pnpm check:api
pnpm build
pnpm test
pnpm --filter @ism/admin exec playwright install chromium
pnpm test:e2e
```

`generate:api` deterministically writes `frontend/packages/api-client/src/generated/schema.ts`. `check:api` regenerates it and fails if Git detects drift. The admin login smoke test intercepts only the network boundary; the page, form, generated client and response rendering all run in Chromium.

The contract carries password and token fields as `writeOnly`. Tests and fixtures use fictional credentials, and generated artifacts must never contain real secrets.
