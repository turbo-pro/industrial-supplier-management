# 供应商门户邀请管理：首段实现与验收

## 当前交付边界

本次把门户从空目录向安全邀请基础推进，不是完整门户注册交付。已有供应商的内部管理员可签发邀请、查看最近 100 条历史、重新签发替代有效旧邀请、带原因和版本撤销。供应商详情中按需展开邀请管理，不改变既有主档、准入审批或账号权限。

门户注册兑换、独立身份绑定、门户登录/会话、自助资料提交与账号回收尚未实现。当前没有可用注册链接，也不向邮箱发送邮件；界面和接口说明均明确这个边界。收件邮箱只是邀请对象快照，并未验证邮箱所有权。后续不得将此凭证直接转换成租户员工账号或赋予内部角色。

## 规则与安全

- V69 新增供应商模块自身的邀请表，绑定 tenant_id、supplier_id 和递增 generation。读取沿用 `supplier:master:view`，签发/撤销沿用 `supplier:master:update`，均复查当前供应商数据范围，范围外及跨租户按 404 处理。
- 签发只允许 DRAFT/ACTIVE，停用、退出或存在提交中的退出申请拒绝。撤销是收回凭证动作，退出后仍允许有权限人员处理有效旧邀请。邀请本身不授予任何新业务资格。
- 服务端生成 32 字节随机令牌（URL-safe 编码），数据库仅存 SHA-256 哈希。明文只在签发响应出现一次，响应 `Cache-Control: no-store`，历史 DTO 不含令牌/哈希，审计不记录它们。页面不写入 localStorage/sessionStorage，隐藏/关闭后不能再次取回。
- 有效期 1–168 小时，使用数据库 UTC；到期动态显示 EXPIRED，不依赖定时任务。当前状态有 PENDING、EXPIRED、REPLACED、REVOKED，不存在虚构的已注册状态。
- 签发锁定供应商行，携带 latestGeneration 作并发前置条件；同一代同时签发最多一笔成功。新建与替代旧凭证、审计同事务提交。旧代重试返回 409，不重复签发、不返回旧明文。网络结果未知时须先刷新；必要时明确重新签发并使旧邀请失效。
- 撤销必须有原因及当前版本，只允许未过期的 PENDING，重复撤销或旧版本 409。历史保留；列表限最近 100 条，同时返回最新代号。

## 页面与契约

新增 `SupplierPortalInvitations.vue`，在主档详情非编辑区嵌入。按服务端 canManage 显示只读态；错误摘要可聚焦，输入有标签与行内提示，读取失败不冒充空记录，未知签发结果停止继续提交直至重新读取。撤销有显式原因与确认步骤，窄屏表格局部滚动。遵循现有 Element Plus 和仓库设计规范；UI/UX 技能的错误摘要规则用于错误焦点与反馈。

API：GET/POST `/api/suppliers/{supplierId}/portal-invitations`，POST `/api/suppliers/{supplierId}/portal-invitations/{id}/revoke`。OpenAPI 与生成客户端同步。入口复用已有内部登录；没有放开新的匿名路由。

## 验收证据

隔离 MySQL `PortalInvitationIT` 5/5、V25 升级回放 1/1，失败/错误/跳过均为 0。包括：

1. 随机令牌与哈希、代号递进、旧邀请替代及撤销审计。
2. 过期后禁止撤销、跨租户/数据范围拒绝、停用和退出状态拒绝签发。
3. 两个独立线程/事务同时以同一代签发，仅一笔成功。
4. HTTP 参数校验、匿名 401、只读写入 403、签发 no-store、历史不泄露令牌和哈希。此处身份令牌/授权加载器为测试替身，不等于真实门户登录。
5. 隔离库注入审计失败，确认新邀请和旧邀请替代一并回滚、旧邀请仍为 PENDING；移除临时触发器后原代重试成功。触发器 finally 清理，不调整数据库权限或关闭校验。

管理端构建、客户端生成及类型检查通过。Playwright CLI 模拟接口检查签发/撤销、令牌不写浏览器存储、错误摘要焦点、只读无操作、加载失败与恢复，并检查桌面及 375px 截图。模拟页面验收不等于真实后端联合验收；截图在忽略目录 `output/playwright/portal-invitation-*.png`。本机 Node/pnpm 低于声明版本仍有警告，构建有既有大包提示。

复现后端：配置 JDK 17+，运行 `./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test -Dit.test=PortalInvitationIT,PerformanceMigrationUpgradeIT -Dfailsafe.failIfNoSpecifiedTests=false verify`。需要 Docker；仅创建独立测试库，无本地 V25 repair、清库或关闭校验。

页面复现：启动 admin Vite 在 127.0.0.1:4176；专用 Playwright CLI 会话执行 `scripts/browser/invitation-ui-fixture.js`，按快照点击登录和“门户邀请管理”，再执行 `scripts/browser/verify-invitation-ui.js`。这些脚本明确只用于模拟 UI，不提供生产测试控制 API。

## 下段推进

1. 独立门户身份模型、邀请原子兑换与单次使用、租户/供应商绑定、注册策略及匿名滥用防护。
2. 门户专用认证与即时撤销，禁止内部 token/门户 token 互通；租户停用、供应商退出及绑定停用实时阻断。
3. 门户注册/登录页面和供应商限定范围的只读资料入口，再接自助资料与准入业务。
4. 实际邀请页面/注册/身份隔离及故障恢复的真实浏览器端到端验收。邮件等外部渠道必须具备真实环境，不虚构投递成功。
