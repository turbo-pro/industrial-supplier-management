# 支持工作台 HTTPS 代理

[模板](support-workspace.servers.conf.template)同时服务 Console 和管理端的生产构建。两个 HTTPS 来源应明确且彼此独立，浏览器的 API 请求从各自来源转发到同一 Spring Boot 服务。后端的 `ism.support.sessions.enabled` 默认关闭；只有经过部署验收的环境才启用。

1. 构建 Console 时设置 `VITE_ISM_SUPPORT_ADMIN_ORIGIN` 为管理端 HTTPS 来源，构建管理端时设置 `VITE_ISM_SUPPORT_CONSOLE_ORIGIN` 为 Console HTTPS 来源。来源包含协议、主机和可选端口；不要附路径、尾斜杠或通配符。
2. 用各站点的监听地址、主机名、证书、私钥和对应 `dist` 绝对路径替换模板占位符。`BACKEND_URL` 只填后端来源，保持原始 `/api/...` 路径。将两个 `server` 块包含到 Nginx 的 `http` 块中；检查配置并发布静态文件。
3. 验证 Console 的 `Cross-Origin-Opener-Policy: same-origin-allow-popups` 和管理端的 `unsafe-none` 没有被外层网关覆盖。给工作台所在来源使用 `same-origin` 会切断首次交接。页面在身份交接后主动断开 opener。不要让 CDN 缓存 API 响应，也不要将认证头或请求体加入访问日志。
4. 在目标环境重复租户批准、Console 打开工作台、受限读取、主动退出和租户撤回；确认普通管理端登录不受影响。回滚先停用后端开关并撤销或等候旧会话到期，再恢复代理配置；保留审计和现有迁移。

本地可复现命令（JDK、项目 Node 依赖、Chrome、Nginx、OpenSSL 和 Docker 可用）：

```bash
node scripts/verify-reminder-e2e.mjs --support-workspace-proxy
```

该命令从已提交的源码构建两端，使用临时 HTTPS 证书、两个回环端口、专用 Nginx 进程及隔离 MySQL。证书例外仅在此次浏览器上下文中生效；验证结果不替代真实部署域名、证书链和外层代理的复验。
