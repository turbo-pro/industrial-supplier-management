# B0/B1 基础回归与重复启动证据

2026-11-05任务卡。依据PRD40及127包B0-02/04/05/08，承接基础表中仍待核对的迁移、认证、隔离和当前门禁。先完整运行B0InfrastructureIT现有24项，不以旧通过记录代替本轮；失败按最新契约核实，产品缺陷修实现，过期断言仅在有明确规格/独立证据时修正。补双应用上下文/真实HTTP重复启动与会话持久性，核对迁移重复执行不改数据、跨节点令牌状态一致，不宣称跨机器压测或所有发布Gate通过。

FR-01全量基础回归24项和失败分类；FR-02空库与第二节点重复启动/迁移校验；FR-03共享数据库的员工令牌刷新/撤销在另一节点即时生效；FR-04隔离及Trace/错误不暴露密钥。此轮以现有接口/页面为对象，不新增产品API或凭据，不强行增加迁移/页面。测试文件、隔离夹具、可复现命令及追踪文档为修改范围；若修产品缺陷，单列原因和回滚。所有测试使用自建容器/临时存储，不读写旧开发库V25，不repair或关闭校验。

## 实现与失败分类

1. 首轮B0InfrastructureIT为24项、23通过/1失败。失败是系统管理菜单精确清单仍停留V91以前，遗漏已由V91种子及租户支持专项验收确认的TENANT_SUPPORT。仅在精确清单补入该项，没有改成宽松包含或跳过断言。随后完整24项通过，初始化方法后续字典/文件/消息/任务等步骤也继续执行通过。
2. 原基础夹具使用固定`/tmp/ism-it-storage`，可能与其他运行共享。改为本夹具创建的唯一临时目录，AfterAll只删除该目录；没有删除旧共享目录或用户资源。
3. 新增SharedDatabaseAuthenticationIT：同一JVM内启动两个独立Spring应用上下文、独立HTTP端口和连接池，共用自建MySQL；关闭第一个，再启动第三个上下文模拟重启。每次readiness通过，Flyway历史/校验和及选定用户身份/版本记录保持一致，V25 checksum 1394665689。
4. 真实HTTP链覆盖首次强制改密及旧访问令牌失效、跨节点访问、关闭原节点后仍可访问、刷新后在重启节点使用、旧刷新重放撤销另一节点整个族、独立族退出使另一节点拒绝、Console跨节点刷新/重放及Console令牌拒绝用于员工API；伪造tenantId查询不能改变认证用户所属租户。
5. 新HTTP测试首轮在401响应读取时得到空ResponseEntity体，未通过traceId断言。采用JDK HttpClient请求工厂复验后，同一业务路径返回401 JSON及traceId并通过，不放宽响应体断言，不修改服务端错误响应；同时按既有认证契约为logout提供有效Bearer。HTTP连接/读取设定超时，整场景300秒上限，异常也关闭自建上下文与临时存储。

## 自动门禁

`node scripts/verify-foundation.mjs`或根目录`pnpm verify:foundation`：固定根pom和工作目录，即使从scripts目录调用也不依赖当前cwd选择reactor；只选择B0InfrastructureIT与SharedDatabaseAuthenticationIT，强制不跳过测试。Maven失败直接失败；XML缺失、数量非24+1、错误/失败/跳过非0，以及非本次生成的旧报告均拒绝。

`--check-reports`只核对已有结果，不执行测试，输出明确标识该事实；本轮从scripts目录调用该模式已通过，不能将其另计为一次25项测试执行。现有CI的infrastructure-integration完整IT命令保持不变，新增Node报告检查与always上传四份基础报告（7天）。新测试会被既有IT命令发现；本轮仅本地验证并解析工作流，未声称GitHub远端CI已经通过。

## 本轮结果（2026-11-05）

| 验收 | 证据与结论 |
|---|---|
| FR-01 | B0InfrastructureIT 24/24，`output/playwright/foundation-regression/b0-final-results.txt`；初次23/24失败保留`first-results.txt`及`full.log`。覆盖迁移/MyBatis/Flowable样板、Console/员工认证、套餐/初始化、作用域和掩码样板、搜索/表格方案、审计/幂等/租约及既有领域基础 |
| FR-02–04 | SharedDatabaseAuthenticationIT 1/1，`shared-final-results.txt`；三次应用启动、同时两个独立上下文的localhost真实HTTP、共享库轮换/重放/退出及Realm隔离，未使用MockMvc或伪造服务响应 |
| 统一门禁实跑 | `gate.log`：25项无失败/错误/跳过，Maven reactor BUILD SUCCESS且脚本仅接受本次新报告；此次完整调用耗时约3分29秒 |
| 入口与CI | 根package脚本和工作流YAML可解析；scripts目录check-reports通过；不增加依赖、API、迁移或产品页面 |
| 清理 | 自建MySQL容器、应用上下文/监听端口及临时存储已关闭/移除；Docker仅留原开发容器。旧库V25未repair、清库或关闭校验；视觉、打印和规划草稿保留 |

范围限制：B0InfrastructureIT包含Service及MockMvc样板，其中部分权限/外部规则使用测试替身，不能当全业务真实权限或ERP/门禁厂商验收。新增场景的HTTP是真实网络，但节点仍在同一JVM、同一机器；未证明跨机器负载均衡、部署网络/时钟、全部业务写入ID分配、文件Local跨节点共享或Flowable任务多节点行为。全产品字段/审计/下载/后台任务逐入口、H5四端、容量、备份恢复及发布Gate仍待。生产应用代码与接口契约本轮未更改，无产品回滚/迁移要求。

映射：B0-02/04/05/06/08补充当前证据但仍保持部分实现；B1角色/套餐/字典等本轮基础样板通过不替代对应整包验收。下一项继续按127包和GAP队列推进，不以25项绿色宣告项目完成。
