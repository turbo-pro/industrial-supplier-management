# B1 订阅到期站内提醒

## 业务边界

V68 新增订阅提醒台账。只处理 ACTIVE、READY 租户当前已开始的最新 ACTIVE 订阅候选，且套餐版本已发布、结束时间非空；已取消、被替换、未来尚未生效、无固定结束时间或停用租户不发。到期超过 30 天不补发历史通知，避免启用扫描后集中推送旧数据。所有边界按数据库 UTC 时钟判断。

阶段为 7 天内、1 天内和已到期，每订阅每阶段成功发送一次，不每日重复，也不回补已错过的早期阶段。收件人是发送时 IAM 中 ACTIVE、未软删除且持有有效内置 TENANT_ADMIN 角色的账号；不是所有租户用户，也不是 Console 人员。后续新增管理员不会收到该阶段已经成功发送的消息，但会参与后续阶段。没有有效管理员会形成失败记录，恢复管理员后可以重试。

使用消息模块的租户级 `TENANT_SUBSCRIPTION_EXPIRY` 站内模板与现有收件箱，模板可沿用现有模板管理功能调整。明确选择 IN_APP，其他同名邮件模板不会覆盖它；不发邮件、短信、钉钉或企业微信，不伪造外部投递。通知是到期提示，不会自动续订、更改权限或删除业务数据。

## 一致性与失败

平台只查询自身套餐/台账表，通过 IAM 公开 `TenantNotificationRecipients` 接口获取收件人，通过消息模块公开服务投递。后台使用系统身份，人工检查使用已鉴权的 Console 操作者身份并在操作后恢复原控制面上下文。

发送事务锁定租户行，重新确认当前候选与阶段；消息投递、全部收件箱与 SENT 台账同一事务，唯一键为订阅 ID + 阶段，多实例争用不会重复发送。任一投递失败则整批回滚，再用独立事务记录 FAILED、错误码和尝试次数。后续成功会清除错误码并记录本次收件人数、时间；失败记录不能覆盖另一工作实例已经写入的 SENT。数据库本身不可用、连失败台账也无法写入时会抛异常并记录后台错误，不返回虚构成功。

后台失败重试至少间隔 30 分钟；每轮最多 10 页、每页 100 条，游标循环防止前页长期失败阻挡后页。人工检查可以在修复后立即重试，但已 SENT 阶段仍不能重发。请求幂等与阶段去重是两层机制：人工检查沿用 Console 幂等键和审计，实际投递是独立提交事务；若幂等响应事务失败，重试可能返回 SKIPPED 而不是原 SENT，但不会再投递一次。

## 配置与页面

自动扫描默认关闭，不新增部署服务或 Kafka。确认消息模板、管理员账号与历史订阅数据后，在部署环境设置：

```text
ISM_SUBSCRIPTION_REMINDERS_ENABLED=true
ISM_SUBSCRIPTION_REMINDERS_POLL_INTERVAL=300000
```

第二项为扫描间隔毫秒数，默认 5 分钟；默认关闭时人工检查入口仍可用。复用 Spring 后台调度与站内消息，不等于已接入统一任务中心。

Console 订阅面板提供提醒记录，展示阶段、状态、次数、人数、最近尝试及错误码。查看需 `platform:package:view`，人工检查需 `platform:package:assign`；路径中的租户与订阅 ID 共同校验归属。只读人员不展示发送按钮。UI 区分 SENT、FAILED、SKIPPED；失败提供修复/重试提示，未知网络结果保留原幂等键重试，已确认失败后的新尝试生成新键。前后端接口契约及生成客户端同步。

## 验收与剩余项

### 2026-10-19 一键编排与 CI 入口

已增加统一入口，在仓库根执行：

```sh
node scripts/verify-reminder-e2e.mjs
```

前提：Linux/macOS、可用 Docker、配置为 JDK 17+ 的 Java 环境、Node 20.19+、pnpm 10.17.1、`pnpm install --frozen-lockfile` 已完成，以及 Chrome 已安装。macOS 可按需先设置自己的 JAVA_HOME；脚本不安装 JDK、不重建本地开发数据库。浏览器 CLI 固定为 0.1.22，通过 npx 获取，首次运行需要网络。

脚本自行创建临时控制目录，启动 Testcontainers 隔离后端，读取随机端口，选择空闲前端端口并以 strictPort/127.0.0.1 启动 Console，然后调用失败恢复浏览器脚本。只有浏览器检查、Maven 进程和最终数据库报告全部成功，才打印整体 pass 并返回 0。后端启动最多等待 6 分钟、前端 1 分钟、浏览器 3 分钟；错误会保留日志并返回非零退出码。退出时先发送 abort，允许夹具清理容器，再分级停止本轮独立进程组，只删除本轮 mkdtemp 控制目录，不终止其他开发服务。

日志与数据库报告保留在 `output/playwright/reminder-e2e-<时间戳>/`，截图沿用 `output/playwright/reminder-recovery-*.png`。同一工作区不应并发执行，截图和 Maven target 报告仍为共用路径。

`.github/workflows/ci.yml` 新增 `console-reminder-e2e` 独立任务，使用 Java 17、Node 22.14.0、pnpm 10.17.1，准备 Chrome 后运行同一个命令；失败也上传日志、截图及数据库测试报告，保留 7 天，作业总超时 20 分钟。使用普通 pull_request/push 权限，不需要业务凭据，不使用 pull_request_target。

本地完整命令退出 0，夹具 1/1，无失败/错误/跳过；错误 JAVA_HOME 负向检查退出 1，明确显示后端启动失败。测试结束仅保留既有本地开发 MySQL；新建容器和服务已回收，无旧库 repair 或历史迁移改动。本机 Node/pnpm 低于仓库声明版本仍有警告，不代表已验证这些旧版本兼容性；CI 使用声明范围内版本。本轮仅确认本地运行及 CI 配置，尚未取得远端 Actions 运行成功证据。

后续仍需真实断网未知结果恢复及可配置提醒策略，不因一条 E2E 通过而标记完整 B1 验收。

### 2026-10-19 失败恢复脚本化验收

新增 `scripts/verify-reminder-browser.sh` 及两个 Playwright CLI 操作脚本。使用实际页面和后端，不模拟 API：无有效管理员时人工检查返回 FAILED，页面展示恢复说明；夹具先确认消息投递和收件箱均为零、FAILED 台账尝试一次，再恢复两位管理员。页面重试使用不同于失败请求的新幂等键，返回 SENT；再次检查返回 SKIPPED。退出后支持人员可查看已发送台账、无发送按钮，直接构造发送请求返回 403。

最终数据库确认两条投递、两条收件箱、一个 SENT 阶段，尝试次数 2、人数 2、错误码清空。隔离夹具 1/1，通过且无失败/错误/跳过；浏览器脚本退出码 0。已目视检查 `output/playwright/reminder-recovery-failed.png`、`reminder-recovery-sent.png`；只读证据为 `reminder-recovery-readonly.png`。控制台仅 favicon 404、刻意构造的 403 及既有 autocomplete 建议，没有新增脚本异常。

复验方式：沿用下方独立空控制目录及夹具启动步骤，在 Maven 命令增加 `-Dism.browser.recovery=true`。读取 `ready` 文件，用其中的后端 URL 启动仅绑定 127.0.0.1 的 Console 开发服务；待其就绪，在仓库根运行：

```sh
bash scripts/verify-reminder-browser.sh <控制目录绝对路径> http://127.0.0.1:4175
```

需要 npx 和已安装的 Chrome。脚本只创建/关闭专属浏览器会话，前后端服务仍由调用方启动。它会检查夹具 scenario，分两阶段运行页面断言，通过控制文件请求夹具复核失败状态并恢复收件人；最后写入 finish。脚本失败写 abort，夹具失败退出并回收容器；恢复等待最多 30 秒。成功时脚本明确提醒仍需等待 Maven 的数据库断言通过，不能单独按浏览器退出码宣称整体验收成功。完成后关闭调用方启动的前端服务，并回收控制目录。

本轮测试资源已回收，既有本地 MySQL 保留；未修改历史迁移、未 repair。仍未完成后端/前端/浏览器一键全生命周期编排或 GitHub CI 接入、真实断网未知结果恢复及可配置提醒策略；本轮没有重跑全部业务模块回归。前文所述“真实失败恢复待补”仅对本次覆盖的无收件人失败场景已补齐，不代表所有异常均已覆盖。

### 2026-10-19 真实浏览器联合验收

已使用 Playwright CLI、实际 Console 开发服务器、随机端口 Tomcat 和全新 Testcontainers MySQL 完成联合验收，未拦截或模拟任何 API 响应：

- 管理员账号密码登录，进入租户订阅计划及提醒记录，初始无记录。
- 首次人工检查显示向两位管理员发送，台账显示 7 天内到期、已发送、尝试 1 次、收件人 2。
- 再次检查显示不重复投递，台账仍为尝试 1 次、收件人 2。
- 退出并用支持人员重新登录，可读取相同台账；发送按钮不存在，显示只读说明。浏览器直接构造写请求返回 403，不能靠隐藏按钮绕过权限。
- 浏览器完成后，夹具核对数据库投递数 2、收件箱数 2、成功阶段数 1 且尝试次数 1；夹具测试 1/1，失败/错误/跳过为 0。退出后独立数据库和后端自动回收，开发服务及浏览器也已停止。

证据为本地忽略目录 `output/playwright/reminder-real-sent.png`、`reminder-real-readonly-full.png` 及 CLI 快照；已目视检查只读完整页面。控制台错误仅 favicon 404 和刻意验证的越权请求 403，无页面脚本异常。没有修改本地旧数据库或历史迁移。本轮没有重跑全部模块测试，不能标记整个 B1 完成。

新增显式运行夹具 `ConsoleReminderBrowserFixture`，正常 `*IT` 扫描不启动它。复验流程（Docker、JDK 17+、前端依赖已准备）：

1. 用 `mktemp -d /tmp/ism-browser-control.XXXXXX` 创建独立空目录，记下实际绝对路径。
2. 运行 `./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test -Dit.test=ConsoleReminderBrowserFixture -Dism.browser.fixture-dir=<上述目录> -Dfailsafe.failIfNoSpecifiedTests=false verify`，保持运行。夹具 `ready` 文件写入实际后端 URL，只绑定 127.0.0.1。不要使用本地业务库。
3. 另一个终端在仓库根运行 `ISM_CONSOLE_API_TARGET=<ready中的URL> pnpm --filter @ism/console dev --host 127.0.0.1 --port 4175 --strictPort`。若端口被占用，选择空闲端口，不终止其他服务。
4. 用专用 Playwright CLI 会话打开该前端，使用 `browser-admin` / `Console#Pass123`，完成上述首次发送及重复检查；再退出，用 `browser-reader` / 同密码检查只读状态。所有账号仅存在于临时库，不用于部署。每次交互使用最新快照定位。
5. 完成后在控制目录创建 `finish` 文件。夹具执行数据库断言并退出；十分钟内未收到完成信号会失败并回收容器。无发送或重复发送都会使数据库断言失败，而不是仅靠页面提示通过。
6. 关闭本轮浏览器会话和前端服务；控制目录只含本轮 ready/finish 标记，可自行回收。

该流程目前由 CLI 操作串联，尚未合并为全自动 CI 浏览器作业；失败反馈、断网重试的真实浏览器覆盖、更多时间阶段和可配置提醒策略仍待补齐。前轮模拟页面覆盖不能替代这些场景的真实联合验收。

### 2026-10-19 真实 HTTP 增量验收

SubscriptionReminderIT 改用随机端口启动真实 Tomcat；新增 TestRestTemplate 用例通过 HTTP 登录获取令牌，验证管理员发送 SENT、相同幂等键重放原结果、新键检查 SKIPPED、支持人员读取成功但发送 403、租户/订阅归属不匹配 404、匿名读取 401。最终数据库仅有两条投递，提醒阶段尝试次数为 1。原有五项用例一并通过，共 6/6，失败、错误、跳过均为 0；数据库由 Testcontainers 独立创建和回收，不使用本地业务库。

Console 开发代理支持 `ISM_CONSOLE_API_TARGET`，默认仍为 `http://127.0.0.1:18080`。例如在仓库根运行 `ISM_CONSOLE_API_TARGET=http://127.0.0.1:18082 pnpm --filter @ism/console dev --port 4175 --strictPort`，可将独立前端连接到自行启动的隔离后端；该命令不会创建或修改后端数据库。变量也可通过 Console 目录的本地 Vite 环境文件设置，仅用于开发代理，不是生产部署配置。

Console 构建通过，既有模拟接口浏览器回归 4/4 通过。真实 HTTP 用例不等于新提醒面板的真实后端浏览器联合验收，后者本轮仍未完成；不能将本轮结果宣称为完整端到端验收。复现后端测试：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test -Dit.test=SubscriptionReminderIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

需可用 Docker 和 JDK 17+；其他操作系统应改用自己的 JAVA_HOME。测试未修改历史迁移，未 repair、清库或关闭校验。

隔离 MySQL 的 SubscriptionReminderIT 验证阶段递进/去重、有效管理员筛选、跨租户隔离、后台默认关闭、候选状态过滤、多实例并发、同名非站内模板、无管理员失败恢复。针对第二位收件人的收件箱插入注入数据库故障，确认此前投递也全部回滚、失败台账可见、后台退避及修复后恢复发送。临时触发器只在测试容器内创建并 finally 清理。

本轮额外定向回归 V25 升级、订阅时窗、取消权限/幂等、分配取消争用及租户开通/原消息发送；不是整套 B0 全组或 B0–B8 完整验收。最终结果见交接记录。

2026-10-19 最终结果：SubscriptionReminderIT 5/5、B0 相关定向 4/4、PerformanceMigrationUpgradeIT 1/1，失败/错误/跳过均为 0。客户端生成与类型检查、Console 构建通过，原浏览器回归 4/4 通过；Node/pnpm 版本低于仓库要求仍有警告，但构建退出码为 0。V25 校验和保持原值，未 repair、清库或关闭校验。

新页面通过 Playwright CLI + 模拟响应检查网络中断保留幂等键、明确失败、恢复发送及重复跳过的反馈；不以模拟浏览器结果冒充真实后端浏览器联调。仍缺固定新面板 CI 用例、真实后端浏览器联合验收、完整模块依赖与套餐例外、可配置提醒策略/渠道及分布式压力测试。外部消息渠道尚未集成。

已检查桌面成功/失败/跳过与 390px 只读面板截图。只读人员可以看状态但没有发送按钮；模拟网络中断时复用同一请求键，收到明确失败后再次尝试使用新键。控制台仅有 favicon 404 和刻意中断请求的网络错误，无新增脚本异常。证据保存在本地忽略目录 `output/playwright/expiry-reminder-*.png`，临时浏览器和开发服务已停止。UI 技能的错误恢复与可访问性规则促使页面使用明确提示、刷新/重试入口及 role=alert，避免将 FAILED 或 SKIPPED 显示为成功。
