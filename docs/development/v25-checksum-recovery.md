# 本地 V25 校验失败修复

## 根因与处理

2026-09-27 本地 Docker MySQL 的 V25 成功记录 checksum 为 `1394665689`，已提交 SQL 为 `-156951582`。实际 `per_supplier_evaluation` 没有 `supplier_code`、`supplier_name` 两列。将这两列从原 CREATE TABLE 恢复为最初形式后，按 Flyway 行级 CRC32 算法计算恰为 `1394665689`，并由真实 Flyway 回放测试固定断言。

因此恢复原始 V25，将供应商快照列移到新 V41：先增加可空列，再按 tenant_id + supplier_id 关联主档回填，最后约束为 NOT NULL。评分、状态、版本和已有业务记录不重写。无法找到同租户供应商时不能捏造快照，迁移将失败，需要人工核查；MySQL DDL 不能整体事务回滚，不得对失败的中间态自动 repair 或重试破坏性操作。

本次不更新 flyway_schema_history 里的旧 checksum，不执行 repair、不清库、不关闭 validate，不添加忽略规则。后续只能新增版本化迁移，不再编辑已经执行过的迁移内容。

## 本地升级前检查

旧库实际版本 V25，绩效记录 0 条，孤立/跨租户供应商引用 0 条；租户与供应商各 1 条，合同、项目均 0 条。本次先用 mysqldump --single-transaction 保存完整库，备份为 `tmp/backups/ism-before-v25-fix-20260927.sql`，权限 600，已忽略上传 Git。数据库及文件有用户数据时，应在维护窗口停止其他写入者并保存备份，再升级。

## 兼容边界

恢复只针对真实原始 V25 checksum `1394665689`。如果另一数据库已经执行过误修改的 V25（记录 `-156951582`），不能直接套用本地处理或偷偷改其记录；应另行核对真实结构、数据、版本和备份，明确批准后再制定兼容恢复。不要把这两种数据库混为一谈。

## 自动回归

新增 PerformanceMigrationUpgradeIT：隔离 MySQL 先迁移至 V25，确认原始 checksum 且不存在快照列；插入历史供应商和绩效，再升级至 V41，确认回填名称/编码、原评分与版本保留，validate 通过，再次 migrate 无新增操作。原 bootstrap 完整集成回归继续覆盖全新库和业务模块。

## 实际验收

`./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test verify` 通过；B0InfrastructureIT 15 项、PerformanceMigrationUpgradeIT 1 项均无失败。执行 `./scripts/dev.sh up` 后，后端 `/actuator/health` 返回 UP，管理端 `4173`、Console `4174` 均就绪。原本地库 V25 checksum 仍为 `1394665689`，V41 成功、失败迁移 0 条；快照列存在且均为 NOT NULL，原供应商 1 条保留。
