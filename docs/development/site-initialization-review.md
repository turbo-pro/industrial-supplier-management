# B1-03/04 · SITE-INIT 租户运营模式初始化

2026-10-29，依PRD70及经营场站交付任务卡；状态：本切片验收通过，不代表完整GAP-02交付。

## 契约与验收

公开 `POST /api/console/tenants/{id}/initialize` 新增必填operationMode（SINGLE_SITE/MULTI_SITE）、siteLabel（1–20字）和siteName（1–200字）；MULTI_SITE另须headquartersName。继续使用首管理员三字段及Idempotency-Key。权限仍为platform:tenant:initialize。新客户端须补齐这些字段，缺失时400，不静默猜测企业模式。Tenant摘要增加可空operationMode、siteLabel及字符串defaultSiteId；旧成功幂等记录缺字段可按空值反序列化，不篡改历史响应。

- SITE-INIT-AT-001：SINGLE_SITE只有根SITE及其闭包；不建总部。管理员主组织/原组织偏好指向该场站，场站ID由服务端分配。
- SITE-INIT-AT-002：MULTI_SITE有根总部及首个直属SITE，共三条闭包；管理员主组织保留总部管理归属，租户默认场站明确指向SITE，不拿总部冒充场站。
- SITE-INIT-AT-003：称谓只影响展示；非法模式、缺必填、缺总部或无初始化权限不得写入组织/账号。
- SITE-INIT-AT-004：组织、首管理员、角色权限、元数据、状态、审计和幂等记录同事务；审计失败后无半初始化，同键可重试；成功回执重放不重复创建。
- SITE-INIT-AT-005：两个不同请求并发初始化同一租户仅一个成功，最终结构/模式一致。
- SITE-INIT-AT-006：V85到V86仅增加可空元数据；旧总部ID/类型及V25 checksum不变。现有可信内部四参数初始化调用保持旧结构、返回未分类；公开HTTP通过必填校验禁止利用此兼容分支。
- SITE-INIT-AT-007：真实Console登录→待初始化租户表单→两种结构初始化→摘要更新→最终数据库验证；只读人员无按钮且服务端403。未知网络结果固定原body/键重试，首管理员密码不保存浏览器存储。

## 实现与边界

V86在plt_tenant加operation_mode/site_label/default_site_id，既有记录不自动赋模式。组织结构由IAM公开OrganizationProvisioner在调用者事务内创建，复用组织Mapper/闭包；platform传入服务端雪花ID，不新加跨模块Mapper依赖。原platform重复总部/闭包写SQL移除。新模式初始密码执行既有强度和BCrypt UTF-8长度限制。

Console新增独立初始化表单，沿用既有样式；锁定进行中及未知结果表单，未知结果按原请求重试，错误通过alert显示。新建租户页面本身仍是既有缺口，不能因已有API及初始化表单声称整个租户开通产品已完成。

仅完成PRD70初始化部分。没有新签发场站授权上下文，没有让旧组织切换影响全业务过滤；单转多、存量结构归类、经营场站停用、默认/最近个人偏好、总部汇总分权及各业务归属仍按后续任务执行。

## 回滚

迁移为向前增量，无存量数据重写。已有新模式租户时，旧应用无法正确解释根SITE/运营元数据，不直接回退应用；先停止相关写入，按成对备份/版本恢复方案操作。不得删除新场站或改业务引用作为回滚捷径；不repair V25、不清库、不关闭校验。工作区已有打印/视觉/OpenAPI草稿须独立保留，仅暂存本轮契约差异。

## 实际证据

最终后端命令（JDK21）：`./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test '-Dit.test=SiteInitializationIT,SiteModeMigrationIT,B0InfrastructureIT#shouldProvisionTenantHeadquartersAndFirstAdministrator' -Dfailsafe.failIfNoSpecifiedTests=false verify`，退出0。新模式真实HTTP/事务/权限/重放/并发6/6，V85迁移保留1/1，原租户生命周期1/1，共8项通过；正常Maven单测和模块边界检查同时执行通过。

Console最终类型检查及构建通过，API客户端10/10；将暂存OpenAPI单独生成后，与暂存schema.ts逐字节一致，验证未依赖用户未提交的打印契约。共享契约/生成类型只暂存本轮差异。

首轮专项5项中1失败/1错误：错误地比较包含每次变化traceId/时间戳的整个响应，已改为比较业务data；审计失败触发器使用普通数据库用户缺权限，已改为仅对自建Testcontainer的root连接安装/删除，与既有回滚专项约定一致。首轮真实浏览器停在包装label下select的精确标签定位；改为在已命名初始化表单中唯一combobox定位，并保留失败现场采集。没有为了通过验收关闭服务端权限、事务或校验。

本地证据目录`output/playwright/site-initialization/`：`scope-tests.txt`、`migration-tests.txt`、`legacy-regression.txt`、`frontend-build.log`、`client-tests.log`、`staged-contract-check.txt`；真实浏览器命令 `node scripts/verify-reminder-e2e.mjs --site-initialization` 最终退出0：Chrome页面完成两种模式，单场站成功响应被主动丢弃后表单冻结、原键/原body重试成功；租户摘要显示SINGLE_SITE/工厂与MULTI_SITE/门店，未处理第三租户保留PENDING，支持人员看不到初始化按钮。最终数据库断言1/1。桌面截图已查看，未做全端响应式或移动场站验收。最终浏览器日志`output/playwright/site-initialization-e2e-1793265774134/`；汇总副本`browser-database-assertions.txt`与`desktop.png`在专项目录。首次定位失败记录保留在`first-browser-driver.log`与第一次原始目录。

驱动已加入现有GitHub Actions真实浏览器矩阵；本轮未宣称云端CI已运行。结束时自建Testcontainers、服务和临时控制目录均由驱动清理，Docker仅剩原开发MySQL；未写旧开发库、未repair或关闭校验。Review确认初始化事务包含结构/账号/元数据/审计、HTTP不能走旧兼容入口，后续场站上下文仍独立待做。
