# Architecture

项目采用Monorepo和模块化单体。`ism-bootstrap`是唯一后端启动模块；业务模块不能直接访问其他模块的Mapper、Entity或数据库表。

稳定跨模块调用通过公开应用接口，跨模块副作用通过领域事件和Outbox。Console在代码和路由上保持独立边界，为后续拆分控制面预留能力。

租户套餐模块通过公开接口 `TenantModuleEntitlements` 传递给 IAM，由平台实现读取已发布套餐模块。IAM 仅使用模块代码集合过滤自身权限及菜单，不查询套餐存储表；后续控制面拆分时替换接口实现。
