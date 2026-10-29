# B1-04/07/12 · SITE-UPGRADE 单场站无损升级

日期2026-10-29，来源PRD70/SITE-AT-008。任务状态：本切片验收通过，不覆盖全部场站上下文或总部业务分权。

## 契约与范围

`POST /api/console/tenants/{id}/upgrade-to-multi-site`，请求headquartersName及当前租户version，必填Idempotency-Key，独立权限platform:tenant:upgrade。返回原Tenant摘要。只允许ACTIVE/READY、已明确SINGLE_SITE、有唯一有效根SITE及完整合法组织闭包的租户；未分类旧租户不自动改义，多场站不降级。不顺便新增第二场站，升级后按组织维护另增场站。

新增总部并将原根SITE挂到总部下；原场站ID/编码/名称/默认场站、内部部门ID/层级、历史业务引用、账号主组织/当前组织、角色/组织授权保持不变。原场站parentId和version变化；新增总部的祖先关系覆盖原子树。不新增账号组织归属或角色授权。原场站子树授权不扩到新父总部/兄弟场站；已有TENANT_ALL继续按其租户全范围语义生效，不能承诺它排除新节点。

结构写入统一先锁IAM租户结构行；组织创建也采用此锁，避免升级与新增部门交错导致闭包漏祖先。平台升级先锁租户控制面并校验版本，升级/状态变化按固定顺序执行。全过程与审计/幂等同事务，不 repair 或重建旧业务。

## 独立验收目标

- UPGRADE-AT-001：有部门及历史业务的单场站升级成功，原节点/归属/账号/授权不变，新增总部及完整闭包；称谓及默认场站保留。
- UPGRADE-AT-002：无权限403、旧版本409；未分类/已多场站/停用或损坏结构拒绝，无部分写入。
- UPGRADE-AT-003：同键成功重放不重复建总部；双客户端同版本只有一个成功。
- UPGRADE-AT-004：强制审计失败整笔回滚，原请求可重试；组织创建与升级交错后闭包正确。
- UPGRADE-AT-005：真实Console确认原场站ID、输入总部、升级并刷新摘要；丢成功响应后原请求/键重试；只读人员无升级入口。

## 修改范围与回滚

V87仅增平台权限及管理员授权，不改旧业务；IAM公开结构升级契约/组织创建锁、platform状态/版本/审计、Console独立表单、OpenAPI及生成客户端、隔离MySQL和真实浏览器专项。共享打印契约只暂存本轮差异，其余用户视觉和规划草稿保留。

事务失败自动回滚；已成功后无“多转单”逆向入口。不得删除总部、改场站ID或批量迁业务来模拟回滚；应用回退必须核对新结构兼容性，按成对备份/版本恢复。只在自建隔离库注入失败，不操作现有开发库或V25历史。

部署限制：所有组织写节点必须先升级到共同结构锁实现。多节点部署时先停止组织结构写入，统一更新节点后再开放升级操作；混跑旧版写节点没有本轮安全保证。升级需要读取/锁定本租户完整组织及闭包，配置写入按租户串行；容量与滚动发布故障演练仍由后续Gate验证。

## 证据

最终JDK21命令 `./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test '-Dit.test=SiteUpgradeIT,SiteInitializationIT,SiteModeMigrationIT' -Dfailsafe.failIfNoSpecifiedTests=false verify` 退出0：升级专项8/8、原初始化6/6、V85向前迁移1/1，共15项通过，正常Maven单测/架构与API门禁同时通过。升级专项预置隔离库中的历史供应商/项目和两层部门，通过真实HTTP验证升级；并非宣称这些业务的整个生命周期重新验收。

专项包括真实双请求版本竞争、审计失败500全回滚及同键重试、坏闭包拒绝且不repair、跨租户默认场站损坏拒绝、旧租户/多场站/暂停/权限/输入边界。实际在performance_schema观察两个写入者等待同一IAM租户结构行，再释放锁，两者完成后新增部门具有正确总部祖先；本轮没有分别强制两种先后顺序，也不称为全部并发穷举。真实授权加载器确认原场站ORG_SET不包含新增总部和兄弟场站，TENANT_ALL仍按原语义生效。

Console类型及构建通过、客户端10/10；暂存OpenAPI单独生成与暂存schema.ts逐字节一致，未混入现有打印契约草稿。真实Chrome命令 `node scripts/verify-reminder-e2e.mjs --site-upgrade` 退出0：确认原场站ID→另一真实请求修改租户版本→旧表单409→取消刷新重开→升级成功响应被丢弃→原body/原键重试→模式摘要更新→只读人员无按钮。最终库断言1/1，模式版本3且只有一条升级审计，原用户/主组织/当前组织和默认场站不变，另一个单场站租户不变。确认表单及桌面结果截图已查看，未声称移动端或全业务场站上下文验收。

证据在`output/playwright/site-upgrade/`：`upgrade-tests.txt`、`initialization-regression.txt`、`migration-tests.txt`、`frontend-build.log`、`client-tests.log`、`staged-contract-check.txt`、`browser-database-assertions.txt`、`confirmation.png`、`desktop.png`。原始浏览器日志在`output/playwright/site-upgrade-e2e-1793269263385/`。最初13项已通过，Review澄清TENANT_ALL语义后增补授权及权限迁移验证，最终15项通过；没有关闭权限或迁移校验来通过验收。

Review已逐项核对锁顺序、当前读/禁用查询缓存、坏结构拒绝、幂等审计事务、String场站ID及页面固定版本/未知响应重试。新场景加入已有GitHub Actions浏览器矩阵，未宣称云端CI已实际运行。驱动清理自建服务、控制目录、Testcontainers和存储目录；结束Docker仅剩原开发MySQL，未写旧库或外部系统。SITE-CONTEXT、场站停用、总部汇总/明细/编辑分权及全入口业务归属仍未完成，不能据此标记整个B1或GAP-02完成。
