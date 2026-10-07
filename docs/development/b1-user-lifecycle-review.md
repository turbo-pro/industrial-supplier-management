# B1 租户用户生命周期增量验收

## 本轮范围

- 管理端 `/system/users` 提供租户用户列表、创建、停用/恢复入口；创建时指定主组织和角色，首次登录要求改密。
- `PUT /api/access/users/{id}/status` 接受目标状态和预期版本，需 `iam:user:manage` 与幂等键；服务端锁定租户内固定用户行并复核目标用户。拒绝跨租户、自己停用、停用最后一名有效租户管理员、移除其最后管理员角色及陈旧版本。
- 停用使 `token_version` 增加并撤销刷新令牌；恢复仅允许重新登录，不复活旧令牌。角色变更同样撤销会话。状态变更走既有审计与幂等处理。
- 用户列表/创建/状态结果包含角色 ID，管理端显示角色名称并支持修改既有用户角色。`PUT /api/access/users/{id}/roles` 要求提交预期用户版本，替换成功后返回更新后的用户视图并提升版本；旧调用方须补 `version` 字段。陈旧版本、跨租户目标及移除最后一名有效管理员角色被拒，修改自己角色后管理端要求重新登录。
- 即使持有 `iam:user:manage`，非租户管理员也不能创建带 `TENANT_ADMIN` 角色的用户、给用户授予该角色，或调整已有管理员账号；该约束在服务端执行。
- `PUT /api/access/users/{id}/password-reset` 仅允许租户管理员重置他人密码，输入临时密码与预期版本；禁止跨租户、自重置、弱密码及复用当前密码。成功后强制下次改密、清除锁定/失败次数、提升版本和 token_version、撤销刷新令牌；旧密码失效。临时密码只经请求传入，不回传、不写审计；幂等只持久化请求哈希。页面不缓存临时密码，建议用安全渠道交付。
- 未修改数据库迁移；旧库 V25 校验和不做 repair/clean/关闭验证。

## 自动化证据

- `./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test -Dit.test=B0InfrastructureIT#shouldEnforceTenantUserStatusAndLastAdministratorSafety -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false verify`：隔离 Testcontainers MySQL，1/1 通过、0 跳过；覆盖租户隔离、最后管理员、自停用、版本冲突、角色读写和令牌失效、非管理员提权拒绝、恢复后重新登录，以及密码重置的管理员权限、跨租户、强度/复用、旧会话失效、临时登录强制改密与最终改密。
- `corepack pnpm generate:api` 与 `corepack pnpm --filter @ism/admin build` 通过，生成契约客户端类型；当前 Node 20.12 低于 Vite 推荐 20.19，构建产生版本提醒但本轮成功。
- `PLAYWRIGHT_CHANNEL=chrome corepack pnpm exec playwright test tests/user-management.spec.ts`（在 `frontend/apps/admin`）1/1 通过；模拟 API 验证页面禁用自停用按钮、状态请求版本/幂等键、创建请求主组织/角色。该用例不是连真实后端的持久化 E2E。
- `PLAYWRIGHT_CHANNEL=chrome corepack pnpm --filter @ism/admin test:e2e`：管理端现有 4/4 浏览器模拟接口用例通过，用户用例覆盖角色编辑及密码重置请求的版本、幂等键和自重置按钮禁用。开发服务器对未模拟的供应商首页请求提示本地后端 18080 不可用，不影响上述用例；不能据此声明真实服务端联调完成。

## 未完成边界

- 角色调整当前是整组替换，不含角色授权影响预览或批量账号调整；修改自己角色后必须重新登录。
- 页面获取角色名称及可选项依赖 `iam:role:view`；仅授予 `iam:user:manage` 而不授予角色查看的自定义角色，仍可调用受控后端写接口，但页面会禁用创建和角色编辑。
- 自助找回密码、租户账号独立锁定解锁、Console 平台账号管理、批量操作与身份联邦未在本轮验收。管理员重置要求管理员已经掌握目标用户身份，不等于自助恢复流程。
- 页面 E2E 使用模拟接口；仍需真实服务+隔离数据库的浏览器回归与更完整并发验收。
