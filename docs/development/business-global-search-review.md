# 业务全局搜索（供应商、合同、项目）验收

管理后台顶部“业务搜索”进入独立页面，支持关键词、对象类型、精确状态、更新时间范围和分页。查询结果可打开对应台账并带入关键词。当前业务目录只接入供应商档案、合同、项目；搜索方案 API 仍保留，页面暂未配置个人方案管理。

搜索 POST `/api/search` 沿用 `search:global:use` 入口权限，并对每类结果分别复核 `supplier:master:view`、`contract:view`、`project:view` 及对应数据范围。SQL 固定租户条件：供应商支持全租户/组织/创建人；合同支持全租户/组织/负责人/创建人；项目支持全租户/组织/项目集合/负责人/创建人。无权限或空集合直接跳过，不执行无范围 SQL；客户端传入的类型不能扩大访问权。仅返回列表已有的名称、编号、状态与更新时间，不检索联系人或敏感证件。

验收：`SearchServiceTest` 覆盖类型权限、范围选择及空范围；隔离 MySQL `B0InfrastructureIT.shouldSearchBusinessRecordsWithTenantPermissionAndObjectScope` 覆盖双租户、组织、项目集合及无对象权限。`./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test verify` 与管理端构建通过。未新增数据库表或迁移；当前 `%关键词%` 模糊匹配在大数据量下的性能和真实后端浏览器 E2E 尚未验收。搜索结果只作导航，不是详情授权凭证，目标台账继续自行判权。
