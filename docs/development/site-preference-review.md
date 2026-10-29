# B1-07/12 · SITE-PREFERENCE 授权候选与个人首选场站

2026-10-29。承接SITE-UPGRADE，作为SITE-CONTEXT前置闭环，不等于签发场站上下文或全业务切换已完成。

契约：GET /site-preferences读取本人状态（preferredSite、unavailable、version）；GET /site-preferences/options按q/page/size搜索授权候选；PUT /site-preferences按本人版本保存siteId或null清除，必填Idempotency-Key。独立操作权限iam:site:view / iam:site:preference，数据资源iam:site。候选必须有效SITE且所有祖先有效，用户有效组织归属可到其下属场站或本部门所在场站，并与该资源授权集合相交；TENANT_ALL不绕过组织归属，其他无适用归属范围拒绝。总部不是候选。

保存仅改变个人首选，不改iam_user_context、主组织、角色或业务过滤。已保存项失效时返回unavailable和版本，不返回该场站ID/名称、不静默另选；原记录保留至本人重选或清除。每次保存/回执重放前复核目标当前授权，版本写入与审计/幂等同事务，用户和租户互不共享记录。

验收目标：PREF-001单场站/总部多场站/部门归属/范围交集及分页搜索；PREF-002本人保存、清除、双客户端版本竞争与同键重放；PREF-003撤权/场站或祖先停用后不泄露旧选择，旧键不得绕过当前授权；PREF-004无权限、跨租户目标和他人偏好隔离；PREF-005审计失败回滚、真实管理端保存/刷新/冲突/丢响应原请求重试及最终库。

范围：V88个人偏好/资源权限/菜单，IAM服务和API，客户端契约，独立管理端页面，针对性隔离数据库与真实浏览器验收。默认菜单和初始授权只给内置租户管理员；其他角色显式授权。无新增服务或外部连接器。保留现有视觉/打印/规划草稿，只暂存相关差异。

回滚：仅向前增加私有偏好表和权限种子；应用回退不自动删表/偏好/审计，确认旧版可忽略后再回退。不得借回滚改变用户组织或修复V25。测试只用自建隔离库。最近使用、签发上下文、全局选择器与逐业务入口过滤仍后续实施，不通过页面保存行为伪装全局隔离。

## 验收与Review

最终偏好专项 `./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test '-Dit.test=SitePreferenceIT,SiteModeMigrationIT' -Dfailsafe.failIfNoSpecifiedTests=false verify`（JDK21）退出0：偏好9/9、升级迁移1/1。先前同轮组合执行的SiteUpgradeIT回归8/8通过。另单独执行B0InfrastructureIT#shouldProvisionTenantHeadquartersAndFirstAdministrator，1/1通过：原“菜单5项”断言改为逐项核对原5项加SITE_PREFERENCE，不删旧菜单检查。正常Maven架构/API单测随这些verify通过。

偏好专项为真实HTTP/隔离MySQL，覆盖总部多候选、部门归属、授权交集、分页和字面百分号搜索、本人版本/清除/重放、读者隔离、跨租户及总部目标拒绝、祖先停用、撤权后不返回旧ID/名称、旧键拒绝、审计失败全回滚、双客户端竞争。已有事务先读取、外部HTTP保存后再按新版本写入的用例验证当前读；版本行FOR UPDATE并禁用查询缓存，保存不改用户组织上下文。V85向前迁移保留旧总部/未分类元数据/V25 checksum，旧内置管理员回填新权限、自定义只读角色不回填，偏好表为空。

最终管理端类型检查与构建通过、客户端10/10；暂存OpenAPI独立生成与暂存类型逐字节一致。接口模拟2/2覆盖失效/只读、读取失败重试、375/768/1024/1440宽度及键盘焦点。另将HEAD与本轮暂存源码导出到独立临时目录、随机端口再跑同样2/2通过，排除未提交视觉/打印草稿依赖；干净副本375截图已查看，副本和服务已清理。

真实Chrome `node scripts/verify-reminder-e2e.mjs --site-preference` 最终退出0：选择→另一真实请求保存造成旧版本409→重新读取→保存成功响应被主动丢弃→原body/原键重试→刷新保留→375宽度无横向溢出→清除→独立只读账号。最终库1/1通过：本人记录版本3、清空、仅一条用户记录/三次真实变更审计，iam_user_context完全不变。工作区桌面/移动截图已查看。首次浏览器因Element Plus占位层拦截内部输入框点击失败，按实际控件外层和禁用状态修正；第二次发现通用409说明缺乏恢复指引，补为“首选场站版本已变化，请重新读取后保存”，固定页面源码后最终复验通过。没有强制点击、去掉权限或绕过校验。

本地证据 `output/playwright/site-preference/`：preference-tests.txt、migration-tests.txt、upgrade-regression.txt、legacy-initialization-tests.txt、frontend-build.log、client-tests.log、ui-tests.log、staged-contract-check.txt、browser-database-assertions.txt、desktop.png、mobile.png；干净源码结果在clean-ui-evidence/。最终浏览器原始日志`site-preference-e2e-1793273471220/`，前两次失败记录保留。新增场景已纳入既有GitHub Actions矩阵，未声称云端CI实际运行。所有自建容器/服务/控制目录/存储及临时副本清理，Docker只剩原开发MySQL；未操作旧库。

本轮交付的是授权候选与首选设置。没有登录自动进入、最近使用、签发/校验场站令牌、全局切换或业务数据过滤；首选也不是业务访问权。下一步复用候选规则建立请求级场站上下文与会话绑定/过期/撤权验证，再依SITE-BUSINESS逐入口接入，不能据此标记SITE-CONTEXT或完整B1/GAP-02通过。
