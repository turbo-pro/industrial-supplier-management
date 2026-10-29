# B0-07 幂等键输入边界

日期：2026-10-29。承接B0-03真实上传错误验收发现的前置缺口，挂靠B0-07/B0-IDEM-AT-001/002；仅完善已有Idempotency-Key格式边界，不增加任务包或改变业务范围。

现有sys_idempotency_record.idempotency_key最多128字符；旧服务没有前置检查，空白键能入库，超长键到SQL层才失败。当前服务在认证租户上下文之后、计算请求摘要/分配ID/写库/执行业务之前，拒绝null、空白、超过128字符及ISO控制字符。返回既有COMMON_VALIDATION_FAILED/400，不回显键值、请求体或数据库错误。

## 独立验收

- IDEM-INPUT-AT-001：八种非法键均400，Mapper、ID生成器、结果处理和业务回调均未调用；缺租户上下文仍优先拒绝，不能通过非法键绕过租户边界。
- IDEM-INPUT-AT-002：真实HTTP上传中空白/超长头无上传或幂等写入；128字符键可以正常初始化，原请求重放得到相同结果且仅有一个上传会话。
- IDEM-INPUT-AT-003：同一完整上传继续完成分片/合并/鉴权下载；既有十并发幂等、不同请求冲突、审计脱敏/回滚、Outbox和任务接管回归仍通过。

单测2/2、真实HTTP上传闭环1/1、既有幂等并发/接管集成1/1通过，无失败、错误或跳过；同轮正常Maven单测包括API契约13/13和模块边界门禁。证据位于output/playwright/foundation-2026-10-29/：idempotency-input-tests.txt、idempotency-http-tests.txt、idempotency-concurrency-tests.txt和idempotency-input-final.log。

复现：JDK21下 `./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test '-Dit.test=HttpInputBoundaryIT,B0InfrastructureIT#shouldAuditSanitizeRetryIdempotentlyAndRecoverExpiredLeases' -Dfailsafe.failIfNoSpecifiedTests=false verify`。

## Review与部署

保留有效键原文，不自动trim或转换大小写，不修改唯一索引/校对规则、历史记录、重放、请求哈希或租户/账号隔离语义。历史非法键不会被清理，但新请求不能再重放它们；使用空白/超长键的旧客户端应改用合法键。无迁移，回滚代码即可恢复旧输入行为，不能repair或清库。

Review无本子任务阻断。隔离容器与临时存储按测试生命周期清理；未改现有开发库。幂等过期/清理策略、全部业务操作接入与响应快照敏感字段治理仍需后续专验，不宣称整个B0-07完成。下一轮转回B1依赖及GAP-01/02/03的业务任务卡，避免无止境扩展通用错误处理。
