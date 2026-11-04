# GAP-01 租户支持策略与授权决定

2026-11-04任务卡，承接PRD68平台审批后的缺口。完成租户默认关闭策略→绑定本租户角色/限定基础字段/最长有效分钟→查看平台已批准申请→租户批准/拒绝/撤回→Console只查看当前授权交集，不创建支持会话或任何业务令牌。

默认要求逐申请租户审批，无自动同意开关。只支持既有SUPPLIER_BASIC_READ_V1。角色仅作权限上界，不扮演租户用户：必须具备有效供应商只读动作及TENANT_ALL或有效组织集合；CREATED/OWNED等依赖用户身份的范围拒绝。批准保存不可变组织/字段快照及策略版本；未来角色扩大不扩大已批准上界，当前角色收紧、组织失效、权限/模块撤回会收紧/拒绝，策略变更使旧批准失效，显式撤回不能恢复。字段始终在请求固定基础字段内，无敏感字段/写入/搜索/导出。

所有租户API只取认证TenantContext，Console身份不能伪造租户审批。IAM通过公共SPI提供角色有界元数据，platform只查询自身表，不访问IAM Mapper/表。Console的授权预览也不能开通会话。审批期限为申请到期与当前策略最长时限的较早者。租户、策略、申请、决定按固定顺序锁定，幂等、版本、审计同事务。未实现的令牌/横幅/业务投影/访问日志继续闭锁。

验收TS-01默认关闭/本租户策略与角色候选；TS-02双审批/拒绝/撤回及政策版本；TS-03角色扩大不扩权、收紧同步、主体/模块/期限失效；TS-04跨租户/跨身份/越权/伪造角色字段拒绝；TS-05并发/幂等/审计回滚；TS-06V90升级保留/V25；TS-07管理端与Console真实双端及最终库、错误/空/只读/窄屏。范围common SPI、IAM角色适配、platform支持策略、V91、管理端新页面和独立路由/菜单、Console预览、契约/客户端/验证。保留原视觉/打印草稿，共享文件仅暂存本轮差异。回滚停策略/审批写入后回退应用，保留决定历史，不删数据、不repair V25；不宣称旧二进制或混版已验证。

## 实现契约与边界

- `GET/PUT /api/tenant-support/policy`：正租户认证；view/manage分权。缺省策略版本0、关闭；创建版本1，任何保存递增版本，不恢复旧批准。`GET /role-options`只返回本租户有效上界角色。
- `GET /api/tenant-support/requests[/{id}]`：只见本租户平台已批准提案或有本租户决定的历史；草稿/外租户404。列表分页有上界，未加载/失败不显示为授权有效。
- `POST /api/tenant-support/requests/{id}/{approve|reject|revoke}`：review且view；强制真实租户AuthPrincipal。请求版本、策略版本、决定版本及幂等键共同约束；批准复制详情的scopeHash与数据库UTC截止，409重新读取。拒绝不授权，撤回不恢复；历史决定不能作为当前访问依据。
- `GET /api/console/support-requests/{id}/authorization`：Console身份、view及本人/独立审批队列可见性；有效租户/申请人/模块/策略/本版批准/期限/当前角色全部成立才返回受限交集。始终`sessionEnabled=false`，不提供进入、业务搜索、下载、编辑或令牌。失败不返回租户字段/组织上界。
- IAM自有Mapper经common公共SPI输出有界角色资料；platform不查询IAM表。套餐专用当前读使用同事务锁定查询（包含订阅选择子查询），避免外层幂等事务RR旧快照；不额外获取嵌套事务连接，不改变普通登录的模块读取契约。
- 冻结审批组织后，TENANT_ALL扩权不能扩大旧组织集合；当前组织收缩、角色动作撤回/停用、模块撤回会拒绝或缩小交集。基础字段只允许ID/编码/名称/类型/状态，联系人、证件、银行账户、附件及任何敏感字段均不在本提案中。
- 管理端菜单`支持授权`与`/system/support-authorization`，Console详情提供授权交集检查；丢失响应锁住原键和原内容，后续403也不能解除未知状态。操作回执与当前状态分开，重新检查失败清除旧就绪展示。

## Review发现与处理

1. 首轮新增“申请人手工锁定”测试用被锁定账号再次登录检查，触发423并污染后续测试。改由有独立审批权限的Console身份读取，测试保留申请人锁定/解锁断言；初轮失败未作为通过。
2. 套餐当前读不能使用幂等事务已建立的RR快照。新增`SupportRoleAuthoritySnapshotIT`建立旧快照后通过独立连接提交模块撤回，要求委托角色解析拒绝。最终实现使用锁定读取，避免嵌套事务连接池争用。
3. 视觉Review发现已决定申请仍展示重新计算的待批截止，改为显示已保存的冻结批准快照/原截止，并明确历史上界不等于当前有效；定向UI断言保存截止不同于新预览时不混淆。Console重新检查失败及任何申请变更清除旧交集，不把历史回执当作现态。管理端只读账号也显示原角色名称；策略/申请错误提供聚焦提示及重读入口。
4. 首轮真实浏览器定位Element Plus隐藏input失败，改为等待按钮可操作后点击可见开关与选择器，不使用强制点击或跳过业务断言。失败记录保留。

## 复现

使用JDK17+、Docker及Chrome：

```sh
./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test '-Dit.test=TenantSupportIT,TenantSupportMigrationIT,SupportRoleAuthoritySnapshotIT' -Dfailsafe.failIfNoSpecifiedTests=false verify
node scripts/verify-reminder-e2e.mjs --tenant-support
corepack pnpm --filter @ism/api-client test
```

浏览器驱动创建隔离Testcontainers库/端口；租户配置→平台已批准申请→租户明确批准→丢失回执原请求重放→Console交集满足且会话关闭→租户撤回→Console拒绝，并由fixture核对最终决定、租户审计与原账号/角色绑定不变。仅清理驱动自建资源。迁移V90→V91保留原供应商、既有权限及V25 checksum 1394665689，重复migrate零变更；不接触用户旧开发库。

后续仍属GAP-01/B1部分实现：一次性进入令牌与浏览器绑定、真实支持会话、双身份租户认证、固定横幅、实际业务字段投影、全链路读取审计、活动会话即时撤销及跨批门禁。须在这些共同可验收前保持业务入口关闭；本轮不宣称支持访问整包完成，也不扩展新业务需求。

## 本轮验收结论（2026-11-04）

| 验收 | 结果与证据 |
|---|---|
| TS-01–05 HTTP、隔离、上界冻结、角色收紧、策略重审批、撤回、到期、申请人/模块撤回、并发、审计回滚、幂等 | `TenantSupportIT` 7/7，最终锁定读实现上复验通过；`output/playwright/tenant-support/TenantSupportIT.txt` |
| TS-03 外层RR旧快照仍必须拒绝已提交的模块撤回 | `SupportRoleAuthoritySnapshotIT` 1/1；同事务锁定读取实现上通过，无嵌套事务连接；同目录报告及`locking-final.log` |
| TS-06 V90→V91、原供应商/权限与V25保留、无自动策略/批准、重复迁移 | `TenantSupportMigrationIT` 1/1；同目录报告。迁移文件完成后未再修改 |
| TS-07 HEAD加本轮暂存源码，隔离临时检出 | 管理端2/2，Console26/26；两端构建通过；`admin-clean-ui-tests.log`、`console-clean-ui-tests.log`、`admin-final-result.txt`、`clean-result.txt`及对应构建日志。不依赖未提交视觉/打印草稿 |
| TS-07 真实管理端/Console/HTTP/MySQL最终闭环 | 1/1；`output/playwright/tenant-support-e2e-1793806052599/database-assertions.txt`、`real-browser-final.log`、`browser-database.txt`。先满足交集且会话关闭，再撤回立即拒绝；Console令牌不能读供应商，未实现session端点拒绝；未知回执原键原体重放，审批仅一条，租户双审计，账号/角色绑定不变，无供应商新增 |
| 接口与客户端 | 精确暂存OpenAPI重新生成类型字节一致；客户端10/10；`staged-contract-check.txt`、`client-tests.log` |
| 窄屏/键盘与视觉Review | 模拟及真实375/768/1024/1440无文档横向溢出，表格局部滚动；错误聚焦、重读、空态、只读及键盘刷新；截图`clean-mock-375.png`、`clean-mock-1440.png`、`tenant-approved.png`、`console-ready.png`、`console-revoked.png`、`tenant-final.png`已检查 |

结论：仅本任务卡的策略、决定、受限交集与页面闭环可独立验收。GAP-01及B1-01/07/12仍为部分实现，不将此结果提升为真实支持访问或127包全部完成。旧开发库未repair/清库/关闭校验，临时库和测试服务由驱动清理，原视觉、打印及规划草稿保留。
