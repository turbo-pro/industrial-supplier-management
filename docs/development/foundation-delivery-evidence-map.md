# B0/B1 前置依赖与验收证据定位

2026-11-05补充：[基础门禁](foundation-regression-review.md)24+1本轮通过；以下10-29行是历史定位，不能代当前整包完成。当前支持提案/租户授权、场站与字典增量分别见最新专项，完整会话和全业务Gate仍缺。

日期：2026-10-29。来源为 PRD 35、40、66、68、70 及仓内源码和专项 Review。本表定位现有基础、已运行范围与下一动作，不将代码存在或历史通过写成整包完成。具体修改入口以仓库实际路径为准。

## 已选择任务

B0-01 子任务：Maven 模块注册/依赖图、基础模块反向依赖、唯一启动模块、跨模块 Mapper 引用和四端目录注册自动检查；移除 bootstrap 对 IAM ConfigurationMapper 的直接依赖。验收号 `ARCH-AT-001–005`，范围仅 bootstrap 测试、观察期适配器和 IAM 观察期公开读取契约。无需迁移、页面或 HTTP 协议调整。回滚程序即可，观察期配置和审批历史不变。

| 包 | 当前实现定位 | 本次或历史证据 | 下一步及未覆盖边界 |
|---|---|---|---|
| B0-01 | 父/子 pom.xml，四端 package.json，bootstrap 启动器 | 本次 ModuleBoundaryTest；见下文 | 仍需跨模块 SQL/内部对象治理；mobile 仅目录注册 |
| B0-02 | bootstrap Flyway、MyBatis 配置，本轮验证V1–V92 | 本次隔离 MySQL 应用启动；旧库升级证据见 v25-checksum-recovery.md | 2026-11-05三次应用启动历史/校验一致；仍不代表全部数据库兼容和发布Gate |
| B0-03 | common-infrastructure/web 统一响应/异常/traceId | 本次[HTTP输入专项](http-input-boundary-review.md)契约13/13、真实上传下载1/1 | 缺头/原生参数误报500已修；全产品消息、请求大小与全部参数组合仍待映射 |
| B0-04 | iam/auth 的 AuthService、JWT、刷新/退出 | 2026-11-05完整24项及新增跨节点真实HTTP1本轮通过 | 员工/Console跨节点令牌族已本轮复验；跨机器/安全事件全量/跨端矩阵仍待，不以门户代员工 |
| B0-05 | TenantContext、TenantIsolationInterceptor、TenantScopedMapper | 本次 shouldFailClosedAndIsolateTenantReadsWritesAndExports | 全业务 Mapper/下载/统计与后台任务的逐入口覆盖仍需归档 |
| B0-06 | AuthorizationGrantLoader、数据范围和字段权限 | 同一隔离用例检查详情/导出掩码、越权、权限变更 | 全产品敏感字段用途/留存/导出治理未完成 |
| B0-07 | operation 审计/Outbox/租约；dispatch-lease-fencing-review.md | 2026-10-28 租约15/15为历史专项证据 | 本次[幂等键输入](idempotency-input-review.md)单测2/2、HTTP及并发集成各1/1；进程宕机/容量、全部副作用幂等及审计保留仍需专验 |
| B0-08 | contracts、api-client、现有浏览器运行脚本 | 多个已有跨端专项 Review；本次没有替代性全量E2E | 四端完整矩阵缺移动H5，当前工作区有他人接口/客户端调整 |
| B1-01 | iam/console 独立认证与平台权限 | b1-console-user-management-review.md 为历史证据 | GAP-01 支持会话不可由平台角色替代 |
| B1-02 | platform/packageplan，IAM 套餐权限交集SPI | 套餐/有效期/额度/争用专项为历史证据 | 容量与全部降级影响、完整端到端门禁继续核对 |
| B1-03 | platform租户与IAM公开组织初始化契约 | [运营模式初始化](site-initialization-review.md)：后端8/8、真实Console及最终库1/1 | 初始化部分通过；完整新建租户页面、存量归类、GAP-02后续场站闭环仍缺 |
| B1-04 | IAM组织树/闭包、初始化及结构升级公开契约 | [初始化](site-initialization-review.md)及[单转多升级](site-upgrade-review.md)：15/15后端、真实Console及最终库1/1 | 场站停用/经营场站上下文及现场业务归属仍待；总部不得冒充场站 |
| B1-05 | iam/access 的 AccessService；账号生命周期 Review | 已有停用/角色/改密/锁定/批量状态证据 | 岗位、邀请交接、安全事件、全部留存规则尚未全映射 |
| B1-06 | AccessService、NavigationService，角色菜单按钮/API | b1-tenant-role-management-review.md 等历史证据 | 支持临时授权、完整权限依赖与场站分权仍缺 |
| B1-07 | 数据范围/字段权限及 IAM 套餐范围约束 | 基础租户/范围/掩码样板；[账号范围](b1-user-scope-consistency-review.md)、[个人场站候选/首选](site-preference-review.md)及[请求级上下文](site-context-review.md)专项 | 总部汇总/明细/编辑分权及支持会话字段交集仍缺 |
| B1-08 | iam/navigation、套餐菜单交集 | b1-package-permission-entitlement-review.md 历史证据 | 新模块/新增入口纳入菜单和后端联动验证 |
| B1-09 | IAM 字典系统项/租户覆盖/自定义项 | ConfigurationService/Test 和初始化历史流程 | GAP-03 核心/扩展字段、表单布局、发布/历史版本未落地 |
| B1-10 | IAM 配置、租户品牌和受控普通用户品牌读取 | b1-tenant-branding-review.md 历史证据 | 2026-11-05 [Console平台品牌](platform-branding-review.md)基础四字段及公开/受权页面已验收；公告、主题/独立登录文案与完整配置分层仍缺 |
| B1-11 | PackagePlanService 影响预览与 TenantService 状态 | 已有套餐降级/未来取消历史专项 | 注销保留/删除、安全审计与全部资源影响待补齐 |
| B1-12 | 多个 IAM/Console 专项及浏览器运行入口 | 历史证据分散，未宣称本次整包复验 | 联合 SUPPORT-AT、SITE-AT、CFG、GAP-13/15 形成完整 Gate |

## P0 补充契约定位

- GAP-01 / PRD 68：已有[提案/独立平台审批](support-request-review.md)和[租户授权/冻结交集](tenant-support-review.md)专项；真实会话、一次性进入、双身份业务认证、横幅和访问日志仍未交付，入口保持关闭，不把Console登录或交集预览当成业务访问。
- GAP-02 / PRD 70：已有初始化、单转多升级、服务端场站上下文及项目工作区专项。未在本轮复验所有SITE-AT；停用、全部业务归属及总部汇总/明细分权仍按原契约逐项核对，不能把旧描述当作这些功能尚无代码。
- GAP-03：已有配置键和字典，不等于自定义字段/动态表单。配置元数据、发布版本、历史引用和字段权限需独立子任务，禁止动态SQL/任意脚本绕开核心规则。
- GAP-13：有字段掩码和部分门户会话清理，不能覆盖全部敏感数据的用途、保留、删除、审计归档和账号交接。
- GAP-15：CI 有编译、单测、隔离 MySQL 和多个浏览器入口；tests/performance 仍是占位说明。容量/P95、备份恢复RPO/RTO、进程宕机与升级回滚不能据此标记通过。

## B0-01 自动检查与边界

`ModuleBoundaryTest` 在 Maven 正常单测阶段执行，现有 CI 的后端 verify 自动包含，无新增工具或服务依赖：

- ARCH-AT-001：聚合目录与实际模块一致，内部依赖目标存在，无环，基础 API 不反向依赖、基础设施只依赖 API，业务不得依赖 bootstrap。
- ARCH-AT-002：唯一 Spring Boot 启动器在 bootstrap；四端包名、Maven Wrapper、前端锁文件存在。目录检查不代表移动端业务或独立构建已经交付。
- ARCH-AT-003：拒绝跨模块显式导入、完整类名及通配导入后使用 Mapper；识别 MyBatis 注解且不以 Mapper 命名的接口。仅共享 TenantScopedMapper 标记作为约定公开接口。
- ARCH-AT-004：构造反向依赖/未知模块/依赖环反例，必须被检查拒绝；本模块 Mapper 和公开应用接口继续允许。
- ARCH-AT-005：观察期通过固定键的 IAM 公开读取接口；租户从服务器上下文取得，保留 Mapper 当前读与原审批事务；默认7天、非法值阻断和待审快照规则不变。

这是源码/依赖描述符检查，不是完整 Java 语义分析。反射、运行时代理、自定义SQL跨表、跨模块内部对象和多层目录仍需后续架构治理，不为历史越界查询建立隐式通过名单。已有跨表组织查询必须另外迁移为所属模块公开读取契约。

本次 ModuleBoundaryTest 5/5、观察期策略2/2、公开读取契约2/2通过；真实MySQL的绩效/限制/解除完整流程及租户隔离/字段掩码2/2通过；现有API契约基线5/5通过。报告位于 `output/playwright/foundation-2026-10-29/`，无失败、错误或跳过。复现：JDK21下 `./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test '-Dit.test=B0InfrastructureIT#shouldPersistPerformanceRuleScorecardAndReview+shouldFailClosedAndIsolateTenantReadsWritesAndExports' -Dfailsafe.failIfNoSpecifiedTests=false verify`。Review确认未改变观察期规则、当前读与审批事务，未发现本子任务阻断。不会修改旧库、repair V25或清理客户数据。

2026-10-29 后续交付：B1-05/06/07 已补 [批量账号角色闭环](b1-batch-user-roles-review.md)，含真实预览、版本竞争、响应丢失重放、数据范围与最终库验证。完整账号列表/单账号入口的数据范围一致性仍须联合复查，岗位、交接及跨租户支持会话继续待推进。


2026-10-29补证：B1-07/GAP-02已有[独立项目场站工作区](site-project-review.md)，真实HTTP专项6、模拟UI8、真实Chrome/最终库1及客户端10通过，服务器归属/原权限求交及历史不迁移有证据。原项目写入口尚待新模式规范化，跨业务查询/打印/导出等未接入；不据此通过全局场站隔离、字段权限或B1 Gate。


2026-10-30补证：B1-07/GAP-02[项目规范入口](project-entry-review.md)已强制新模式写入上下文及组织读/详情分权，PROJECT全局搜索无总览权也不返回。主体11项、搜索增量1项、升级1项、独立副本UI8项/构建、真实Chrome/最终库1项通过；V25校验和不变、历史项目不迁移。不是跨模块完整SITE-AT-004或B1 Gate。下一前置B1-03 Console租户创建→订阅→初始化用户链，当前按钮无事件已核对。


2026-10-30补证B1-03：[Console租户创建链](tenant-creation-review.md)已由最小授权候选→创建/首订阅→初始化→首次强制改密→唯一场站读闭环验证；主体HTTP7、初始化回归6、页码扩展1、独立副本UI9/构建、真实Chrome/最终库1及客户端10通过，无迁移或历史归类。B1-03配置种子完整性/Gate仍待；下一补B1-02套餐创建与版本模块额度/发布页面，现有“新建套餐”按钮仍未绑定行为。

2026-11-04补证B1-02：[套餐配置发布链](package-authoring-review.md)已接通创建→模块额度→草稿修改→校验→发布→新租户候选/订阅，修复首次发布不激活父套餐的断点。专项HTTP7、必填version/主体补验2、既有额度回归1、独立源码UI14/构建、客户端10及真实Chrome/最终库1通过。父套餐/版本/模块使用当前锁定读并重新校验，已发布不可覆盖，无DDL。`requireQuota`尚未接入供应商等实际写入口，不能据此将B1-02或额度限制判完成；下一优先补供应商配额业务闭环。

2026-11-04补证B1-02：[供应商档案额度执行](supplier-quota-review.md)通过common预留/聚合SPI，平台串行锁与域内当前计数接入唯一建档服务；缺失/0/订阅失效无新增容量，建档/计数/审计同事务。读取/降级使用全租户保留主体实时数。最终HTTP7、原订阅/额度2、架构5、独立Console UI17/构建、客户端10、真实Chrome与最终库及API扩容恢复1均通过，无DDL/旧库改写。剩余其他资源计量、合并、完整容量与Console订阅分配操作面，不能宣布B1-02整包完成。下一优先分配/升降级确认页面。

2026-11-04补证B1-02/11：[Console订阅分配确认](subscription-assignment-review.md)完成选择/影响/立即未来分配/未知原请求恢复/计划刷新及取消。HTTP7、旧时窗1、独立Console UI21/构建/契约一致、客户端10、真实Chrome/最终库1及旧额度浏览器1通过。无DDL，旧Java4参可信调用保留，HTTP须补tenantVersion/activationMode；不宣称全部菜单流程影响完成。下一转GAP-01受控跨租户支持访问，不无限追加套餐接口。

2026-11-04补证GAP-01/B1-01/07/12：[支持访问提案与平台审批](support-request-review.md)完成本人草稿/提交、独立审批/拒绝、取消、轨迹、权限撤回回执门禁及有序审计。V90增控制面表和分离权限，旧数据/V25保留。HTTP7、升级1、独立UI25/构建/契约一致、客户端10及真实双身份/最终库1通过。只完成平台提案决定，不生成租户业务能力；完整支持访问和B1 Gate仍未完成。下一租户授权策略/审批及IAM交集，再进入会话阶段。
