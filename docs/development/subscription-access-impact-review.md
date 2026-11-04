# B1-08 / B1-11 套餐菜单和权限影响闭环

2026-11-05任务卡，承接套餐分配仍缺菜单影响、B1-08尚待核对的前置缺口。范围：分配预览列出当前有效套餐到目标版本的活动页面目录、操作权限和字段权限的失效/恢复数量及有界明细；实际分配后验证同一租户已有JWT菜单/API同步收紧，恢复套餐不改写角色绑定及业务数据。

界限：目录能力比较不是逐账号菜单模拟；目录存在不代表账号获得角色授权。基础SYSTEM/COMMON/MESSAGE/WORKFLOW沿用IAM现有制度不随套餐撤回。当前数据库时刻比较，未来计划不预言执行时的角色/目录/用量；提交与每次请求继续执行原服务端权限/额度/版本校验。工作流实例处理、数据保留/导出和全部资源计量仍待原计划，不标整包完成。

契约：在现有POST /console/tenants/{tenantId}/subscription-preview及分配回执追加authorizationImpact；count按kind和change统计，最多200条活动目录白名单明细、总数/截断显式，不含租户账号、角色名或业务正文。common公开只读SPI由IAM实现自有目录查询，platform不查询IAM Mapper/表。页面由Console分配页展示真实预览，无模拟成功。

验收SA-01失效/新增/无变化及基础模块；SA-02已停用目录/模块及细分字段、边界和截断；SA-03只读Console/租户伪造身份拒绝；SA-04真实降级菜单隐藏且直调业务403，恢复后原绑定/业务原样；SA-05角色未授予时不因套餐恢复自动扩权；SA-06Console错误/空/有界明细/四宽键盘及真实双端最终库。既有迁移不变，无新库表需求。回滚回退应用版本，新增响应字段对旧消费端可忽略；不修改或repair V25、不删角色/菜单/权限/业务数据。

## 实现和Review

- `ModuleCapabilityCatalog`位于common-api，IAM用自有全局目录Mapper实现，只返回活动页面目录（不计Layout容器）、ACTION和FIELD元数据。platform通过SPI组合当前有效模块与目标版本的活动模块，未访问IAM表或内部Mapper。
- 目录比较与IAM实际授权共用`GrantEntitlements.withCoreModules`基础模块制度；失效项优先，模块/类型/编码稳定排序，最多200项，合计含截断未展示项。停用目录/全局停用模块不误报为可恢复能力。
- `authorizationImpact`是参考目录快照，不是账号授权、锁定确认哈希或未来计划模拟。实际请求继续按当前套餐与原角色求交；不清空或扩大原角色权限。旧幂等回执可能缺少新增字段或为null，契约显式允许，页面要求重新预览且禁止把缺失当作零变化。仅新预览保证提供快照。
- 不改变既有额度策略：已有用量超出目标上限仍拒绝分配。本轮真实降级样例无供应商记录，验证的是菜单/权限恢复与绑定保留，不宣称已交付有存量业务数据时的模块降级保留策略。
- 首轮测试源有Java局部变量复合声明编译错误，已拆分；没有将编译失败记为通过。首轮真实双端等待“用户管理”子菜单超时；改为独立桌面视口、等待真实导航响应并核对基础菜单/供应商菜单变化，恢复时按实际折叠状态展开。没有强制点击或跳过业务API/数据库断言；两次浏览器日志均保留。
- 模拟375/768/1024/1440无页面横向溢出；有界表格局部滚动，summary键盘展开、缺失/空/截断状态和原未知重试/403/409流程通过。已检查模拟窄屏/桌面与真实降级/恢复截图。本轮没有改写管理端视觉底稿；直达已失去模块的业务路由仍可显示页面外壳/通用错误和原按钮，业务API返回403。统一路由/按钮状态治理继续挂B1-06/08，不能因此把整包标为完成。

## 验收结果（2026-11-05）

| 证据 | 结果 |
|---|---|
| SA-01–05 HTTP/目录/实际授权 | `SubscriptionAccessImpactIT` 13/13（6项本轮目录/权限链与7项既有分配回归），`output/playwright/subscription-access/backend-results.txt`；包含降级/恢复同JWT、无角色不扩权、停用目录/字段、基础模块、200项边界及权限拒绝 |
| SA-06干净暂存源码 | Console 28/28及构建通过；`clean-ui-tests.log`、`clean-build.log`、`clean-result.txt`。从HEAD加精确暂存差异临时检出，未依赖未提交视觉/打印草稿 |
| 契约/客户端 | 暂存OpenAPI重新生成类型字节一致，客户端10/10；`staged-contract-check.txt`、`client-tests.log` |
| SA-04–06真实双端/最终库 | 1/1，`output/playwright/subscription-access-e2e-1793833039744/database-assertions.txt`、`real-browser-final.log`、`browser-database.txt`；两次分配、三条历史，原角色菜单/权限逐行不变、账号/组织不变，原JWT降级403/恢复200、未授权角色仍403，V25 checksum 1394665689保留 |

截图目录`output/playwright/subscription-access/`：`clean-mock-375.png`、`clean-mock-1440.png`、`downgrade-preview.png`、`restore-preview.png`、`mobile-impact.png`、`admin-downgraded.png`、`admin-restored.png`。管理端截图沿用本地视觉草稿；本轮交付仅修改Console和后端/SPI/契约/测试，不包含该草稿。

复现（JDK17+、Docker、Chrome）：

```sh
./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test '-Dit.test=SubscriptionAccessImpactIT' -Dfailsafe.failIfNoSpecifiedTests=false verify
node scripts/verify-reminder-e2e.mjs --subscription-access
corepack pnpm --filter @ism/api-client test
```

结论：活动目录预览→分配→菜单/API收紧→恢复且不改原绑定的本任务卡闭环通过；B1-08/B1-11仍为部分实现。逐账号影响、完整路由/按钮状态、工作流实例处理、存量业务降级保留/导出及跨批发布门禁仍待原计划；GAP-01真实支持会话/双身份认证/横幅/访问审计/即时撤销仍保持未交付与入口关闭，不扩大为新需求。
