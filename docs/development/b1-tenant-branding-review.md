# B1 租户品牌配置验收

## 本轮交付

- 沿用既有 `cfg_setting_definition` / `cfg_tenant_setting` 四项配置和乐观版本：系统名称、Logo URL、浏览器图标 URL、页脚文字；无新迁移。租户配置页提供独立保存与预览，版本冲突提示刷新；不将多个字段保存伪装为原子事务。
- 新增已认证租户可读的品牌白名单接口 `GET /api/configuration/branding`，仅返回四项有效值；读取不要求配置管理权限，修改仍要求 `system:setting:manage`。不暴露其他租户配置或安全策略字段。
- 管理端登录后应用当前租户品牌，保存后当前会话即时刷新；退出时重置标题和图标。登录页保持通用品牌，避免未认证租户查询及租户枚举。
- 系统名称非空且至多 100 字；页脚至多 300 字；URL 仅允许站内绝对路径或 HTTP(S)，拒绝 `//` 协议相对地址。浏览器端再次筛选图片地址，页脚仅作纯文本渲染。

## 验收边界

- 2026-10-07：`./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test -Dit.test=B0InfrastructureIT -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false verify` 首轮隔离 MySQL 19/19 通过、0 跳过；验证无权限用户可读当前租户品牌、不能读全量配置，且不能读取另一租户覆盖值。随后用 `-Dit.test=B0InfrastructureIT#shouldReadOnlyAuthenticatedTenantsEffectiveBrandingWithoutSettingPermission` 对最终代码重验 1/1 通过、0 跳过，包含空名称及协议相对 URL 拒绝断言。
- `PLAYWRIGHT_CHANNEL=chrome corepack pnpm --filter @ism/admin test:e2e` 3/3 通过；新用例使用模拟接口验证品牌显示、设置保存后更新以及退出重置。模拟接口不等于真实后端浏览器持久化 E2E。管理端构建通过，但当前 Node 20.12 低于 Vite 建议的 20.19。
- 图片目前通过可信 URL 引用，不提供图片文件上传/托管、外链可信域名白名单或失效资源自动回退；平台 Console 独立品牌也未实现。此轮不宣称 B1 品牌能力全部完成。
