# B1 Console 平台账号管理增量验收

## 范围与边界

- V61 为平台管理员新增 `platform:user:view` 和 `platform:user:manage` 权限；平台支持人员没有这两项权限。未修改既有迁移。
- Console 管理页可查看平台账号、创建运营管理员或支持人员、停用/恢复。新账号初次登录必须修改密码；初始密码只经请求传输，不进入响应和审计摘要。
- 状态变更要求目标版本及幂等键；服务端拒绝自停用、陈旧版本、重复状态，以及停用最后一名有效平台管理员。停用/恢复均提升令牌版本并撤销旧刷新令牌；恢复只能重新登录，不复活旧会话。
- 登录与刷新先锁定平台用户行再处理令牌，以与状态变更保持一致锁顺序，防止停用/恢复并发留下可复用的旧刷新令牌。
- 平台账号接口受后端权限保护，与租户账号和业务数据隔离。`PUT /console/users/{id}/role` 替换单一平台角色，拒绝自调整、陈旧版本、重复角色和移除最后一名有效平台管理员；变更后撤销目标会话。
- `PUT /console/users/{id}/password-reset` 仅限平台账号管理员重置他人密码，拒绝自重置、弱密码和复用当前密码。成功后清除输错密码临时锁、强制下次改密、撤销旧会话，但不启用已停用账号；临时密码不回传、不写审计，幂等表只保存请求哈希。需通过安全渠道交付目标用户。
- 本人改密和管理员重置均先锁用户行再写入，并提升账号版本；并发冲突由版本或当前密码校验拒绝，不让较晚请求默默覆盖先前结果。

## 自动化证据

- `./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test -Dit.test=B0InfrastructureIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false verify`：隔离 Testcontainers MySQL，61 条迁移成功，B0 集成测试 21/21 通过；覆盖平台权限边界、首次改密、自操作/最后管理员保护、版本冲突、角色调整与重置后的旧会话失效。随后定向复跑 Console 账号用例 1/1 通过，确证成功写接口的幂等重试及响应不包含临时密码。
- `corepack pnpm generate:api`、`corepack pnpm --filter @ism/console build`、`PLAYWRIGHT_CHANNEL=chrome corepack pnpm --filter @ism/console test:e2e`：页面模拟接口回归 4/4 通过。浏览器用例不等于真实服务端持久化 E2E。
- 当前 Node 20.12 低于 Vite 要求的 20.19；本轮构建虽通过，发布前应升级运行环境。

## 未完成

- 角色授权影响预览、独立手工锁定/解锁、账号批量处理及自助找回密码。
- 真实服务端浏览器回归、并发状态变更专项验证及更完整发布 Gate。
