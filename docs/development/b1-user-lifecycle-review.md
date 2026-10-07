# B1 租户用户生命周期增量验收

## 本轮范围

- 管理端 `/system/users` 提供租户用户列表、创建、停用/恢复入口；创建时指定主组织和角色，首次登录要求改密。
- `PUT /api/access/users/{id}/status` 接受目标状态和预期版本，需 `iam:user:manage` 与幂等键；服务端锁定租户内固定用户行并复核目标用户。拒绝跨租户、自己停用、停用最后一名有效租户管理员、移除其最后管理员角色及陈旧版本。
- 停用使 `token_version` 增加并撤销刷新令牌；恢复仅允许重新登录，不复活旧令牌。角色变更同样撤销会话。状态变更走既有审计与幂等处理。
- 未修改数据库迁移；旧库 V25 校验和不做 repair/clean/关闭验证。

## 自动化证据

- `./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test -Dit.test=B0InfrastructureIT#shouldEnforceTenantUserStatusAndLastAdministratorSafety -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false verify`：隔离 Testcontainers MySQL，1/1 通过、0 跳过；覆盖租户隔离、最后管理员、自停用、版本冲突、登录与刷新令牌失效、恢复后重新登录。
- `corepack pnpm generate:api` 与 `corepack pnpm --filter @ism/admin build` 通过，生成契约客户端类型；当前 Node 20.12 低于 Vite 推荐 20.19，构建产生版本提醒但本轮成功。
- `PLAYWRIGHT_CHANNEL=chrome corepack pnpm exec playwright test tests/user-management.spec.ts`（在 `frontend/apps/admin`）1/1 通过；模拟 API 验证页面禁用自停用按钮、状态请求版本/幂等键、创建请求主组织/角色。该用例不是连真实后端的持久化 E2E。
- `PLAYWRIGHT_CHANNEL=chrome corepack pnpm --filter @ism/admin test:e2e`：管理端现有 4/4 浏览器模拟接口用例通过。开发服务器对未模拟的供应商首页请求提示本地后端 18080 不可用，不影响上述用例；不能据此声明真实服务端联调完成。

## 未完成边界

- 角色分配 API 已有，但用户管理页未展示或编辑既有用户角色；需补读取用户角色关系与页面闭环。
- 租户账号密码重置、锁定解锁、Console 平台账号管理、批量操作与身份联邦未在本轮验收。
- 页面 E2E 使用模拟接口；仍需真实服务+隔离数据库的浏览器回归与更完整并发验收。
