# Architecture

项目采用Monorepo和模块化单体。`ism-bootstrap`是唯一后端启动模块；业务模块不能直接访问其他模块的Mapper、Entity或数据库表。

稳定跨模块调用通过公开应用接口，跨模块副作用通过领域事件和Outbox。Console在代码和路由上保持独立边界，为后续拆分控制面预留能力。

租户套餐模块通过公开接口 `TenantModuleEntitlements` 传递给 IAM，由平台实现读取已发布套餐模块。IAM 仅使用模块代码集合过滤自身权限及菜单，不查询套餐存储表；后续控制面拆分时替换接口实现。

订阅到期提醒通过 IAM 公开接口 `TenantNotificationRecipients` 获取当前租户管理员，再调用消息模块公开服务写入站内通知。平台提醒台账不直接查询 IAM 账号/角色表或消息表；后台调度由 bootstrap 编排，默认关闭。
