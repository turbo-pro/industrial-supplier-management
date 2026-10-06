# B1 租户账号长期未登录改密提醒验收

## 范围与判定

- 仅租户账号；平台 Console 账号不在本轮范围。成功登录前读取 `iam_user.last_login_at`，按租户 `security.inactivePasswordDays` 覆盖值或系统默认 90 天判定。`0` 关闭提醒；没有上次登录时间的首次登录不触发长期未登录提醒。
- 响应 `user.passwordChangeRecommended` 仅是建议，不进入 JWT 强制拦截；已有 `user.passwordChangeRequired` 与后端强制改密门禁保持独立。
- 成功登录后才更新 `last_login_at`；因此同一账号紧接着再次登录不重复提示。改密后原会话失效，页面清除本地登录态并引导重新登录。
- 管理端弹窗可延期处理建议改密，强制改密时不能关闭且不加载工作台业务页面；头部也可主动修改密码。

## 验收证据（2026-10-07）

- `./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test -Dit.test=B0InfrastructureIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false verify`：隔离 MySQL，`B0InfrastructureIT` 17 通过、0 失败、0 跳过；校验 Flyway 59 迁移。覆盖首次强制改密、默认 90 天提醒、再次登录不重复、租户配置 `0` 禁用。
- `corepack pnpm generate:api`、`corepack pnpm --filter @ism/admin build` 通过；Node 20.12 低于工程建议的 20.19，构建仍成功。
- `PLAYWRIGHT_CHANNEL=chrome corepack pnpm --filter @ism/admin test:e2e --grep 'tenant administrator|first login requires'`：2 通过。浏览器验证建议提醒可延后、强制改密提交与重新登录引导。页面接口使用模拟响应，不代表真实后端持久化浏览器端到端验证。

## Review 与后续缺口

- 登录提醒只在本次登录响应中展示；刷新 token 和 `/auth/me` 不重新计算历史长期未登录状态，避免登录后重复提醒。
- 配置沿用已有表与定义，不修改历史 Flyway 文件，不需要新迁移。
- Console 长期未登录策略、密码到期规则、账号停用/解锁审批和真实后端浏览器 E2E 仍未完成；不能将本轮视作 B1 账号生命周期全部交付。
