# Architecture

项目采用Monorepo和模块化单体。`ism-bootstrap`是唯一后端启动模块；业务模块不能直接访问其他模块的Mapper、Entity或数据库表。

稳定跨模块调用通过公开应用接口，跨模块副作用通过领域事件和Outbox。Console在代码和路由上保持独立边界，为后续拆分控制面预留能力。

租户套餐模块通过公开接口 `TenantModuleEntitlements` 传递给 IAM，由平台实现读取已发布套餐模块。IAM 仅使用模块代码集合过滤自身权限及菜单，不查询套餐存储表；后续控制面拆分时替换接口实现。

订阅到期提醒通过 IAM 公开接口 `TenantNotificationRecipients` 获取当前租户管理员，再调用消息模块公开服务写入站内通知。平台提醒台账不直接查询 IAM 账号/角色表或消息表；后台调度由 bootstrap 编排，默认关闭。

门户注册由供应商模块协调，使用共同接口 `PortalRegistrationTenantGate` 取得平台租户锁并复核有效模块，使用 `PortalAccountProvisioner` 由 IAM 创建独立门户身份，不创建内部员工或角色。三个模块的邀请、账号、审计同事务提交，兑换事务使用独立 READ_COMMITTED；注册匿名入口默认关闭且共享数据库限流。专用认证与会话见下段增量。

门户认证后续增量已引入 IAM 专用随机会话，不复用内部 JWT/刷新表；过滤链按命名空间隔离，内部授权快照为 denyAll。复用平台租户/有效模块 SPI，新增供应商状态 SPI 和匿名对端预算 SPI；每次请求实时复核绑定状态，账号/会话只由 IAM Mapper 写入。登录、会话退出与审计具有事务一致性，认证开关默认关闭。门户前端及自助业务仍未交付。
