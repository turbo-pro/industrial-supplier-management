# Architecture

项目采用Monorepo和模块化单体。`ism-bootstrap`是唯一后端启动模块；业务模块不能直接访问其他模块的Mapper、Entity或数据库表。

稳定跨模块调用通过公开应用接口，跨模块副作用通过领域事件和Outbox。Console在代码和路由上保持独立边界，为后续拆分控制面预留能力。
