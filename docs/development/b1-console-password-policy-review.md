# B1 Console 账号改密闭环验收

## 行为边界

- 平台 `plt_user` 与租户 `iam_user` 独立。Console 使用部署级 `ISM_CONSOLE_INACTIVE_PASSWORD_DAYS`（默认 90；0 关闭提醒）比较登录前 `last_login_at`；首次登录没有历史时间不产生长期未登录建议。
- `force_password_change` 继续由 Console 后端强制限制业务接口。页面识别该状态，仅显示改密界面，不请求套餐或租户数据；刷新页面后依旧保持强制改密引导。
- 长期未登录建议不阻断 Console 业务，可在提醒中立即改密或稍后处理；平台人员也可在头部主动改密。
- 成功改密后原令牌失效，页面清除本地登录态并要求用新密码重新登录。刷新令牌及 `/me` 不重新给出已经处理过的长期未登录建议。

## 验收与剩余风险

- 2026-10-07：`./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test -Dit.test=B0InfrastructureIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false verify` 在隔离 MySQL 上 18/18 通过、0 跳过；覆盖后端强制门禁、改密后旧令牌失效、默认 90 天提醒和连续登录不重复提醒。
- `PLAYWRIGHT_CHANNEL=chrome corepack pnpm --filter @ism/console test:e2e` 3/3 通过，覆盖强制改密/刷新与建议延期；接口响应为模拟数据，不代表真实后端持久化浏览器 E2E。`corepack pnpm --filter @ism/console build` 通过。
- 本轮没有数据库迁移或外部账号连接器。长期未登录天数是部署级平台策略，不是租户配置；平台 Console 账号管理、停用与解锁审批，以及真实后端浏览器持久化 E2E 仍待补齐。
