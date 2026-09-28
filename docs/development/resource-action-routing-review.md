# 资源入口受控动作细分验收

人员登记、人员启用、车辆设备登记、车辆设备投入使用分别固定使用 `PERSON_REGISTER`、`PERSON_ACTIVATE`、`ASSET_REGISTER`、`ASSET_USE`。动作由后端业务方法决定，不接受客户端自行指定；均继续复查供应商当前状态、企业级限制和待处置退出申请。人员培训/特种作业凭证、设备状态及项目归属仍由原资源服务独立校验。停用、离场、维修、退役和资产交接是处置路径，不调用新增业务门禁。

限制解释的 OpenAPI 枚举、生成客户端和管理后台动作选项同步增加四项；业务门禁命中台账直接保存准确动作。旧 `RESOURCE_ASSIGN` 保留为兼容解释/历史命中代码，新的四个资源写入口不再使用，不把历史记录重写成新动作。当前判定仍为企业级，不宣称已经支持按人员、设备、组织、项目或地点的局部规则。

验收：`SupplierResourceServiceTest` 覆盖四个固定动作、受限登记和启用/使用不写入、交接不走新增业务门禁；`SupplierReferenceServiceTest` 枚举参数化覆盖 WATCH 警示、限制及待退出阻断；隔离 MySQL `B0InfrastructureIT` 的全动作回归覆盖新枚举。没有数据库结构变化，无迁移。本轮 `./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test verify` 通过，管理端构建通过；契约生成检查在提交前单独执行。
