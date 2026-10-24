# Supplier Portal

仓库根目录运行 `corepack pnpm --filter @ism/portal dev`，访问 `http://127.0.0.1:4175`。

开发代理默认 `http://127.0.0.1:18080`，本应用 `.env.local` 可设置 `ISM_PORTAL_API_TARGET`。生产使用 HTTPS 反向代理 `/api`，不要直接暴露 Vite 开发服务作为公网 Demo。

服务端注册与认证默认关闭，受控环境分别启用 `ISM_PORTAL_REGISTRATION_ENABLED=true`、`ISM_PORTAL_AUTHENTICATION_ENABLED=true`。不得为试用修复/清空旧数据库或关闭迁移校验。管理账号创建邀请后，通过可信渠道提供邀请凭证及租户编码；门户注册后再登录，不会发送邮件或自动批准准入。

密码、邀请和令牌不写浏览器存储。令牌仅存内存，刷新需重新登录，刷新本身不撤销服务端会话；请使用“退出当前会话”，网络失败需重试。

构建 `corepack pnpm --filter @ism/portal build`。Node >=20.19、pnpm 10.17.1。

登录后自动展示“本企业档案”及有效的已关联采购品类，可刷新及失败重试。企业范围由服务端会话绑定，无法切换到其他供应商；档案状态不代表准入审批结论。退出或会话失效时清除档案显示。接口及验收边界见 `docs/development/supplier-portal-profile-review.md`。

档案下可提交简称、行业、国家省市、地址和网站的资料变更申请，审核批准后才生效；支持查看差异、申请结果及本人撤回。企业名称、信用代码等主体资料仍需联系邀请方。管理员给员工显式分配 `supplier:change:review` 后，可在供应商详情的“资料变更审核”中处理；原档案已修改则拒绝覆盖。真实跨端验收：`node scripts/verify-reminder-e2e.mjs --portal-changes`，详见 `docs/development/supplier-profile-changes-review.md`。

档案下可展开“本企业资质证照”，按证书编号/类型关键词及到期状态筛选、分页查看详情、下载该证照关联的原件。租户须开通 QUALIFICATION 模块；内部草稿不发布，过期/撤销/已换证证照仅供留档，下载不代表准入有效。原件不暴露文件ID或存储链接。真实验收：`node scripts/verify-reminder-e2e.mjs --portal-certificates`，详见 `docs/development/supplier-portal-certificates-review.md`。

下方“新增证照申请”可上传PDF/PNG/JPEG原件（最多10MiB及租户上限，1MiB分片、24小时有效，账号/企业滚动24小时最多10/50次初始化），本页可暂停/续传，刷新不保留文件与标识。填写类型、编号及有效期并明确确认后进入管理端资质台账；有核验权限的员工通过/拒绝后，门户刷新申请记录查看结果，通过后可在证照区查阅原件。暂不支持换证/补正/撤回，拒绝原因需联系邀请方；内部核验意见不会外发。提交结果未知时仅可核查或重试原申请，不应换标识重复提交。真实验收：`node scripts/verify-reminder-e2e.mjs --portal-submissions`，详见 `docs/development/supplier-portal-certificate-submissions-review.md`。

登录后可展开“修改我的密码”，校验原密码并设置新密码；成功后所有设备旧会话撤销，必须重新登录。原密码错误与登录共享 5 次/15 分钟锁定。网络结果未知不自动重试，先使用新密码核查。

忘记密码使用“恢复凭证”：部署方显式启用 `ISM_PORTAL_PASSWORD_RECOVERY_ENABLED=true`（同时须启用认证），给管理员分配独立 `supplier:portal:recover` 权限。管理员核实本人身份并填写原因后签发 15 分钟有效、只显示一次的凭证，由可信渠道交付，用户自行设置新密码。支持替换与撤销，不自动邮件找回，也不让管理员直接设置用户新密码。签发不撤销会话；兑换成功才撤销全部旧会话。真实跨端验收 `node scripts/verify-reminder-e2e.mjs --portal-recovery`，详见 `docs/development/supplier-portal-password-recovery-review.md`。

真实改密验收：`node scripts/verify-reminder-e2e.mjs --portal-password`，独立库核对密码哈希、版本、全部会话撤销及本人审计。详细范围见 `docs/development/supplier-portal-password-review.md`。

页面模拟验收：启动后使用 Playwright CLI 打开专用会话，再从仓库根目录执行 `run-code --filename scripts/browser/portal-auth-ui.js`。它不建立真实账号，不是服务端 E2E。详见 `docs/development/supplier-portal-ui-review.md`。

真实联合验收：JDK 17+、Docker、上述 Node/pnpm 及 Chrome 可用时，在根目录执行 `node scripts/verify-reminder-e2e.mjs --portal`。命令启动 Testcontainers 独立 MySQL、随机端口后端与门户，跑真实浏览器，再核对数据库；只有两者通过才退出 0。清理自己创建的临时控制目录和进程，日志保留在 `output/playwright/portal-e2e-*`，不接触旧开发库。CI 安装 Chrome 可使用 `pnpm --filter @ism/console exec playwright install --with-deps chrome`（复用已有工具依赖）。

真实跨端账号管理验收：`node scripts/verify-reminder-e2e.mjs --portal-management`。额外启动随机端口管理端，夹具初始化两个真实员工及不同角色，不模拟认证/权限/API。通过管理端页面停用、恢复、撤销门户会话，验证门户实时失效、恢复不复活旧令牌、只读员工修改 403；最终核对管理版本、四条门户会话、登录退出和操作者审计。证据保留在 `output/playwright/portal-management-e2e-*`。该命令不操作现有开发数据库，也不自动启用生产门户开关。

真实退出回收验收：`node scripts/verify-reminder-e2e.mjs --portal-exit`。申请人与审批人分别真实登录，经管理端页面创建待使用邀请、提交退出、独立审批，再验证门户/旧令牌失效、邀请兑换被拒及封存完整性。仅本场景在隔离库启用 MANUAL 财务模式并准备人工证据元数据和独立复核；不模拟 ERP 成功，也不代表物理文件归档已验证。证据位于 `output/playwright/portal-exit-e2e-*`。
