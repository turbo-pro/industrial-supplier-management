# 退出本地记录封存验收

V48 在本地退出批准事务中写入唯一封存记录：申请、结果、聚合处置项、全部逐实体事项和事件按稳定字段顺序计算长度前缀 SHA-256，并记录记录数、依据文件 ID、批准人及封存时间。封存失败会使批准整体回滚，不能出现本地关闭成功却没有封存清单。驳回、撤回及待处置申请不生成封存记录。

`GET /api/suppliers/{supplierId}/exit-applications/{id}/archive` 沿用 `supplier:exit:view` 与供应商实时数据范围，只返回当前租户供应商的清单元数据。读取时在一致事务中重新计算摘要与数量，`integrityVerified=false` 明确显示不一致，不静默宣称完整。封存和查询均追加审计；不提供修改或删除封存记录的业务 API。管理后台可在已关闭申请中查看封存时间、数量、摘要和校验状态。

这是**本地数据库记录的封存校验**：依据文件只存 ID 引用，没有复制或冻结文件字节；没有法定保管期限、对象存储不可变策略、外部回收回执或门户身份绑定。结果仍为 `LOCAL_BUSINESS`，外部访问仍为 `NOT_VERIFIED`，不对外宣称完整归档或完整清退。

验收：隔离 MySQL 回放 V1–V48；`B0InfrastructureIT` 覆盖批准后生成封存、原始记录校验一致、篡改事件后校验失败、跨租户不可读及测试数据清理；`ExitApplicationServiceTest` 验证批准才触发封存。`./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test verify` 通过（`B0InfrastructureIT` 15 项、0 失败/错误）；OpenAPI 客户端类型已生成，管理端构建通过。真实浏览器端到端和文件字节封存仍待补。
