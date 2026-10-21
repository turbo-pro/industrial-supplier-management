# Supplier Portal

仓库根目录运行 `corepack pnpm --filter @ism/portal dev`，访问 `http://127.0.0.1:4175`。

开发代理默认 `http://127.0.0.1:18080`，本应用 `.env.local` 可设置 `ISM_PORTAL_API_TARGET`。生产使用 HTTPS 反向代理 `/api`，不要直接暴露 Vite 开发服务作为公网 Demo。

服务端注册与认证默认关闭，受控环境分别启用 `ISM_PORTAL_REGISTRATION_ENABLED=true`、`ISM_PORTAL_AUTHENTICATION_ENABLED=true`。不得为试用修复/清空旧数据库或关闭迁移校验。管理账号创建邀请后，通过可信渠道提供邀请凭证及租户编码；门户注册后再登录，不会发送邮件或自动批准准入。

密码、邀请和令牌不写浏览器存储。令牌仅存内存，刷新需重新登录，刷新本身不撤销服务端会话；请使用“退出当前会话”，网络失败需重试。

构建 `corepack pnpm --filter @ism/portal build`。Node >=20.19、pnpm 10.17.1。

页面模拟验收：启动后使用 Playwright CLI 打开专用会话，再从仓库根目录执行 `run-code --filename scripts/browser/portal-auth-ui.js`。它不建立真实账号，不是服务端 E2E。详见 `docs/development/supplier-portal-ui-review.md`。

真实联合验收：JDK 17+、Docker、上述 Node/pnpm 及 Chrome 可用时，在根目录执行 `node scripts/verify-reminder-e2e.mjs --portal`。命令启动 Testcontainers 独立 MySQL、随机端口后端与门户，跑真实浏览器，再核对数据库；只有两者通过才退出 0。清理自己创建的临时控制目录和进程，日志保留在 `output/playwright/portal-e2e-*`，不接触旧开发库。CI 安装 Chrome 可使用 `pnpm --filter @ism/console exec playwright install --with-deps chrome`（复用已有工具依赖）。

真实跨端账号管理验收：`node scripts/verify-reminder-e2e.mjs --portal-management`。额外启动随机端口管理端，夹具初始化两个真实员工及不同角色，不模拟认证/权限/API。通过管理端页面停用、恢复、撤销门户会话，验证门户实时失效、恢复不复活旧令牌、只读员工修改 403；最终核对管理版本、四条门户会话、登录退出和操作者审计。证据保留在 `output/playwright/portal-management-e2e-*`。该命令不操作现有开发数据库，也不自动启用生产门户开关。
