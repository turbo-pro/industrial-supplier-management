# 门户会话保留与批量清理

2026-10-22。内部维护任务，默认关闭；不新增 HTTP 删除接口、管理页面或门户权限，不是账号删除或历史退出补偿。

## 可单独验收的规则

- 默认保留 90 天，最低 30 天、最高 3650 天；这只是产品运维默认值，不是法定留存要求。启用前由部署方确认其审计、备份与保留政策。
- 取 MySQL UTC 时间，一批固定同一个 cutoff；仅删除 `expires_at < cutoff` 且 `revoked_at IS NULL OR revoked_at < cutoff` 的记录。等于 cutoff 不删除；近期撤销的旧过期会话继续保留。即使账号版本失效或已撤销，只要过期时间不满足条件也不删除。
- 默认每租户每轮最多 100 条，可配置 1–500 条；每轮最多发现 50 个租户，按租户 ID 游标前进，到尾部重置。单租户大积压分轮消化，不无限循环拖垮数据库。
- 仅无租户上下文的内部 worker 可发现候选租户，只返回租户 ID；删除必须在系统 actor=0 的指定租户上下文执行。Mapper 显式 tenant_id 并经过已有隔离拦截器，普通员工/门户用户不能调用删除；不要求租户仍激活，停用租户的老会话也可按策略维护。
- 独立 READ_COMMITTED 事务按过期时间/ID 选择 `FOR UPDATE SKIP LOCKED`，多实例跳过被其他事务锁住的记录；删除强制按 PRIMARY 定位选中 ID，再带租户和完整期限条件。避免优化器选择租户/过期范围索引，重新锁住刚跳过的记录。受影响数不等于已锁定数即失败回滚。此处为 MySQL 8 方言，后续信创适配须替换并重跑并发验收。
- 删除与 `PORTAL_SESSION_CLEANUP` 审计同事务，审计 actor=0，只记录截止时间、保留天数及删除数量；不包含令牌、哈希或联系方式。空批次不写审计，不修改账号、邀请、登录历史审计、退出事件或封存。
- 一个租户失败不妨碍后续租户；失败以租户 ID 和异常类型告警，不打印 SQL 参数/凭证。下轮遍历可重试，事务回滚保证不会“删成功但没审计”。没有独立失败台账或主动外部告警，本段不声称这些能力已完成。

V73 只增加全局/租户过期检索索引，没有修订旧迁移；V25 仍需保持原校验和。无需 Redis、Kafka 或额外服务。

## 启用与回退

部署配置：

| 环境变量 | 默认 | 边界 |
|---|---|---|
| ISM_PORTAL_SESSION_CLEANUP_ENABLED | false | 显式 true 才扫描/删除 |
| ISM_PORTAL_SESSION_RETENTION_DAYS | 90 | 30–3650；越小越早删除 |
| ISM_PORTAL_SESSION_CLEANUP_BATCH_SIZE | 100 | 1–500 |
| ISM_PORTAL_SESSION_CLEANUP_POLL_INTERVAL | 3600000 | Spring 定时毫秒值 |
| ISM_PORTAL_SESSION_CLEANUP_INITIAL_DELAY | 3600000 | 启动后延迟一小时 |

保留天数及批大小非法时启动失败；目前是部署级配置，不是租户可自行缩短的配置页。多节点须保持一致配置。启用前备份并检查实际数量与执行计划，先在隔离环境验证，再安排维护窗口。Docker 开发 compose 仅启动数据库，不会自动把宿主机变量注入另一个后端进程；应在实际启动后端的环境配置。

预检只读 SQL（预编译参数 `?` 必须绑定已授权的具体租户，不得去掉租户条件）：

```sql
SELECT COUNT(*) AS eligible_sessions
FROM iam_portal_session
WHERE tenant_id = ?
  AND expires_at < UTC_TIMESTAMP(3) - INTERVAL 90 DAY
  AND (revoked_at IS NULL OR revoked_at < UTC_TIMESTAMP(3) - INTERVAL 90 DAY);
```

若采用其他天数，预检与运行配置必须一致。审计核对按指定租户/动作查 `sys_audit_event`。关闭开关并重启能停止后续扫描，但不能恢复已物理删除的会话行；恢复只能依靠部署方备份。恢复旧库后必须维持令牌撤销/版本语义，不得将备份当作复活登录的工具。此功能不删除审计证据，也不会缩短固定 8 小时的有效会话期限。

## 验收

首轮 23 项门户集成中，锁争用用例因删除语句使用宽范围索引等待已跳过行而失败；没有扩大超时或跳过用例，修复为 PRIMARY 定位后完整重跑。最终 PortalAuthIT 24/24、PerformanceMigrationUpgradeIT 1/1、PortalSessionCleanupWorkerTest 4/4，命令退出 0，失败/错误/跳过均为 0。V73 新库迁移和 V25 原校验和升级回放通过。预期审计故障注入日志不等于验收失败。

diff 检查通过，临时容器已回收，仅原开发 MySQL 保留。未变更既有管理端 UI、接口契约或客户端，因此本段没有前端页面或浏览器验收；没有声称远端 CI 已通过。

集成用例在 Testcontainers 独立库人工构造历史会话，覆盖当前有效会话保留、近期过期/近期撤销/尚未过期但已撤销保留、跨租户不影响、系统身份限制、空批次幂等、201 条跨三批、其他事务锁住首 100 条时继续处理第二批、审计故障回滚及重试。另用固定截止时间验证过期/撤销恰好等于截止时间不删、早 1ms 才可删；直接跨租户 Mapper 参数被拦截，删除时重复期限判断。worker 单测覆盖默认禁用、非法配置、租户失败继续处理且清空上下文、50 租户分页游标。

复现：JDK 17+、Docker 下执行 `./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test -Dit.test=PortalAuthIT,PerformanceMigrationUpgradeIT -Dfailsafe.failIfNoSpecifiedTests=false verify`。没有操作本地旧库或自动打开清理开关，没有 repair、清库或关闭迁移校验。生产规模压力及节点宕机中断仍需进一步专项验证，不以本轮测试代表全部运维验收。
