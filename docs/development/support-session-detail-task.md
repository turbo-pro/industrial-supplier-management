# 支持会话详情实施任务卡

2026-11-08，承接 deacc9a。挂靠 GAP-01 / B1-06、07、12，依据 PRD 68，状态为待实现，不增加原127任务包。

## 契约与范围

新增审计面 `GET /api/console/support-audit/sessions/{sessionId}`，要求平台域与 `platform:support:audit`。返回会话、申请、租户、实际支持操作者、原租户决定和角色的字符串ID，创建/截止/撤销时间、持久状态及到期派生状态，以及创建时冻结的组织/字段范围。返回 `Cache-Control: no-store`。不存在返回404，无权限拒绝；不返回任何进入、浏览器、访问秘密或摘要、原始JSON和供应商正文。

现有 `plt_support_session.granted_scope_json` 已保存 `tenantAll/organizations/fields`，无需新迁移。显示必须明确这是创建时授权上界，不代表当前有效访问权限；审计查询不得调用激活或续期逻辑。实际读取继续走服务端当前权限交集。不要用审计员身份调用原申请人授权校验，也不要把失效会话隐藏掉。

Console 从审计列表进入详情，展示上述元数据和冻结范围，串联已有生命周期及访问记录。切换对象/退出账号时清除旧详情，支持加载、空、拒绝、失败重试；只读详情不自动增加终止或申请能力。

## 可独立验收目标

- 创建会话后改变当前租户角色，历史详情仍显示冻结范围，业务访问仍受当前权限收紧约束。
- 独立审计员能读详情，但支持人员、员工和支持工作台凭证均不能借此获取详情。
- 到期、撤销及强制终止的记录可审计；查询前后会话和凭证字段不变。
- 响应和页面不包含秘密、摘要、原始JSON或未批准的业务值。
- 真实HTTP/隔离MySQL验证权限与历史语义；Chrome通过列表进入详情、切换记录及失败重试；干净提交经HTTPS代理联调，保存最终库证据。

## 修改边界和回滚

仅会话Mapper/服务/控制器、OpenAPI与生成客户端、Console审计组件、针对性测试和验收记录。复用既有主键查询，无迁移。回滚应用/页面即可，保留历史会话和审计数据。保留工作区其他视觉、打印与规划草稿，接口文件按本轮差异单独暂存。

完整批准链、实时有效范围解释若超出本详情契约，单独对照PRD验收，不暗示已完成。匿名安全告警仍独立待办。此卡只锁定实施边界，无新增产品代码或验收通过结论。

2026-11-08 实施断点：工作区已新增 SupportSessionMapper.auditDetail 和受独立审计权限保护的 SupportSessionService.auditDetail；查询返回冻结范围的结构化DTO，未查询凭证列，未改变会话状态。`./mvnw -pl backend/ism-platform -am -DskipTests compile` 通过。尚未接控制器、OpenAPI、页面或专项测试，未提交推送，不计交付。下轮承接这两个已修改文件，继续完成本任务卡的接口、页面与真实隔离库验收后一起提交，勿将其误判为用户视觉改动。

2026-11-08 后续断点：已接审计详情控制器及OpenAPI、生成客户端。干净契约/客户端候选保存在 `output/support-detail/clean.yaml` 和 `clean-schema.ts`，仅供最终差异暂存；工作区仍保留打印契约草稿。客户端生成成功，尚未做HTTP或页面验收，未提交推送。接下来完成Console详情交互及真实隔离库用例，不把生成成功当成验收。

2026-11-08 实施进展：Console审计列表已接详情按钮、加载/失败重试、历史批准范围提示与控制信息显示。前端构建通过；`SupportSessionHttpIT#independentAuditorSeesMetadataWithoutOperatorCapabilities`真实HTTP/隔离库1/1、API契约13/13通过，验证撤销详情、冻结字段、凭证不泄露及员工/支持员拒绝。证据 `output/support-detail/http.log`、`build.log`。尚缺页面自动化、历史范围收紧对照及真实HTTPS详情联调，保持未验收、未提交推送；下轮承接所有本任务改动，不重复生成任务卡。

2026-11-09 页面补验：扩展 `support-audit.spec.ts`，首读503后重试成功、冻结组织/字段提示显示、切换生命周期清除旧详情及无审计权不展示入口，Chrome 2/2通过，证据 `output/support-detail/ui.log`。这是模拟接口页面测试，不代替真实服务联调。仍缺历史范围收紧对照及HTTPS详情联调；所有详情相关变更继续保持工作区未提交状态。

2026-11-11 历史语义补验：真实HTTP/隔离MySQL1/1及接口契约13/13通过，证据 `output/support-detail/http-history-verified.log`。自定义租户角色撤去读权限后，原业务凭证403，审计冻结范围不变；到期详情派生EXPIRED而持久状态仍ACTIVE，查询前后数据库整行相同，不存在会话404。首轮Docker未启动及两次测试夹具错误（内置角色禁止修改、到期时间约束）已修正，未修改产品规则或旧库。产品与浏览器脚本已本地提交91f3795，真实HTTPS联调执行中，尚未推送。

2026-11-11 final acceptance: session audit detail delivered in 91f3795/ff8789d. Real HTTP + isolated MySQL 1/1 and API contract 13/13 passed (output/support-detail/http-history-verified.log); Console build and mock Chrome 2/2 passed. Role permission withdrawal rejects business reads while frozen historical scope remains unchanged. Expired detail is derived without modifying the stored session; missing session returns 404.

Clean HEAD HTTPS/Nginx/Chrome/two-frontend integration and final isolated database assertions passed: output/playwright/support-workspace-proxy-e2e-1794350531253/database-assertions.txt; driver log output/support-detail/https-recheck.log. Initial browser assertion incorrectly assumed only two approved fields; corrected to allow the actual approved field set. Reviewed output/playwright/tenant-support/session-detail.png: metadata, frozen scope, scope warning and reload control visible. Earlier in-progress entries are superseded by this result.

No migration, no V25 changes, no user draft changes. Rollback application/frontend only and preserve historical data. Production deployment and anonymous security alerts remain unverified; GAP-01 and whole task packages remain open. Next: assess the existing PRD68 anonymous security-event gap without expanding detail scope.
