# 企业级限制解释验收

## 范围与入口

供应商档案页下方选择当前页供应商，打开“限制解释”，选择准入申请、新建项目、资源新增/分配、现场进场、项目开工、项目复工之一，查询全部命中。

`POST /api/suppliers/{supplierId}/restriction-explanation?action=PROJECT_CREATE`，后端权限 `supplier:restriction:explain`，同时校验供应商主档数据范围。V36 只新增权限，不自动授权生产角色；本地演示遵循既有权限初始化方式。无请求体，未知动作由参数转换拒绝。

## 规则与安全 Review

- 租户内企业级范围，当前有效 BLACKLIST、TEMPORARY 全部为 DENY，WATCH 为 WARN；已独立解除及过期临时限制不再命中。
- 准入允许 DRAFT/ACTIVE/SUSPENDED，其余动作要求 ACTIVE；待处置退出申请全部加入 DENY 命中，不以第一条替代全部解释。
- DENY 优先于 WARN，无命中为 ALLOW。ALLOW 仅表示上述企业级检查，不代表资质、合同、项目、人员凭证等具体业务检查通过。
- 返回 `advisoryOnly=true`、`scope=TENANT_ENTERPRISE` 和中国标准时间业务日期。业务提交仍使用已有实时拦截；解释结果不参与授权，也不改变限制或退出状态。
- 在供应商行锁内重新核验当前数据范围，限制及退出申请使用当前读；隐藏供应商不加载命中、不产生查询审计。来源 ID 不包含原始敏感限制原因。
- 每次合法查询写入既有 `sys_audit_event`，记录动作、合并判定、全部来源 ID、业务日期和解释范围；这是查询快照审计，不是已执行业务的 `rst_restriction_hit` 台账。
- 页面查询失败清空旧结论，动作/供应商切换清空结果，失效请求不得覆盖新供应商结果。

## 自动化验收

新增六个服务测试：全部命中与判定优先级、观察与空命中、准入/退出状态差异、隐藏供应商、锁后组织数据范围复核、未知动作拒绝。

隔离 MySQL 集成验收增加：WATCH 返回 WARN、查询审计实际落库、已解除限制排除且剩余临时限制仍命中。延用既有真实 MySQL/Flyway 跨模块回归；不修改旧开发库 V25 校验和。

前端验收为 API 客户端生成、Vue 类型检查和生产构建，不声称已完成浏览器交互或持久化 UI E2E。

2026-09-27 验证结果：`./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test verify` 成功，供应商模块 56 个单测、真实 MySQL 14 个集成测试均为零失败/错误/跳过，隔离库实际执行 V1–V36。`corepack pnpm generate:api` 与 `corepack pnpm --filter @ism/admin build` 成功。现有 Node 20.12 低于引擎要求及前端大包警告仍存在，未擅自升级运行环境。

## 尚未完成

组织/工厂/地点/品类范围、其他对象类型、完整动作目录、版本化规则、业务拦截命中独立表及统一判定 SPI 仍需后续实现。当前解释接口不将未落地的寻源、分包、门禁申请和质量让步伪装成可执行动作，也不能证明外部系统访问回收。
