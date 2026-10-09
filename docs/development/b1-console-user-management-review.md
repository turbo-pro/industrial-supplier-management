# B1 Console 平台账号管理增量验收

## 范围与边界

- V61 为平台管理员新增 `platform:user:view` 和 `platform:user:manage` 权限；平台支持人员没有这两项权限。未修改既有迁移。
- Console 管理页可查看平台账号、创建运营管理员或支持人员、停用/恢复。新账号初次登录必须修改密码；初始密码只经请求传输，不进入响应和审计摘要。
- 状态变更要求目标版本及幂等键；服务端拒绝自停用、陈旧版本、重复状态，以及停用最后一名有效平台管理员。停用/恢复均提升令牌版本并撤销旧刷新令牌；恢复只能重新登录，不复活旧会话。
- 登录与刷新先锁定平台用户行再处理令牌，以与状态变更保持一致锁顺序，防止停用/恢复并发留下可复用的旧刷新令牌。
- 平台账号接口受后端权限保护，与租户账号和业务数据隔离。平台账号角色目前创建时二选一，不提供事后角色变更、密码重置或独立手工登录锁；这些仍在后续范围。

## 自动化证据

- `./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test -Dit.test=B0InfrastructureIT#shouldManageConsoleAccountsWithoutTenantPermissionsOrLastAdminLoss+shouldSeparateConsoleAuthenticationAndEnforcePlatformPermissions -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false verify`：隔离 Testcontainers MySQL，61 条迁移成功、集成用例 2/2 通过；覆盖角色权限边界、创建后强制改密、自停用/最后管理员保护、版本冲突、旧会话失效及恢复后旧刷新令牌不可复用。
- `corepack pnpm generate:api`、`corepack pnpm --filter @ism/console build`、`PLAYWRIGHT_CHANNEL=chrome corepack pnpm --filter @ism/console test:e2e`：页面模拟接口回归 4/4 通过。浏览器用例不等于真实服务端持久化 E2E。
- 当前 Node 20.12 低于 Vite 要求的 20.19；本轮构建虽通过，发布前应升级运行环境。

## 未完成

- Console 账号密码重置、事后角色调整/授权影响预览、手工锁定/解锁、账号批量处理。
- 真实服务端浏览器回归、并发状态变更专项验证及更完整发布 Gate。
