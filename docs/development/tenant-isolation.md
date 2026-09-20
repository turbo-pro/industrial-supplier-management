# 租户上下文与 MyBatis 强制隔离

## 上下文来源

租户业务请求的 `tenantId` 只来自服务端验证后的访问令牌。客户端 Header、查询参数或请求体不能切换当前租户。`JwtAuthenticationFilter` 在进入业务代码前建立 `TenantContext`，请求结束后在 `finally` 语义下清理，避免线程复用导致上下文泄漏。

平台 Console 不自动拥有租户业务上下文。后续需要协助客户时必须使用 B1 的限时支持会话，不能增加可任意指定租户的 Header。

## Mapper 约束

所有读写租户业务表的 MyBatis Mapper 必须实现 `TenantScopedMapper`。执行前由 `TenantIsolationInterceptor` 强制检查：

- 当前线程存在已认证租户上下文；
- `SELECT`、`UPDATE`、`DELETE` 的 `WHERE` 包含 `tenant_id = #{tenantId}`；
- `INSERT` 显式写入 `tenant_id`；
- 绑定的 `tenantId` 与认证上下文完全一致。

任一条件不满足时 SQL 不会发送到数据库。平台控制面表、全局字典定义等非租户数据 Mapper 不实现该接口，并由独立模块和权限域管理。

## 后台任务

异步任务、导出和消息消费没有 HTTP 请求上下文，执行每个租户分片时必须显式使用：

```java
try (TenantContext.Scope ignored = TenantContext.open(tenantId, systemActorId)) {
    // 调用 TenantScopedMapper
}
```

禁止在一个上下文中查询多个租户的业务正文；跨租户控制面汇总必须使用不含业务正文的专用投影和平台 Mapper。
