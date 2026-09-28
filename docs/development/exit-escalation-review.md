# 退出逾期升级验收

V47 增加租户配置 `exit.escalationRecipientId`（默认 `0`，关闭）和 `exit.escalationAfterDays`（默认 3，允许 1–365 天）。管理后台租户配置页使用已有后端配置权限和版本/幂等机制，保存非原子，失败须刷新核对。接收人保存时必须是本租户有效用户，发送前再次复查；已停用或模板不可用时事务回滚并进入自动催办失败台账，不记录为送达。

只有租户已开启自动催办、退出申请仍待审、逐实体事实仍未结清、已逾期且达到配置天数，并通过原催办间隔与多实例并发控制时，才会给责任人发催办，并向不同的指定负责人发独立站内升级通知。通知沿用实体业务引用，文案明确不代表完成或责任转移。重复升级受自动催办间隔约束；不会改派、关闭本地事项，亦不会宣称外部账号/门禁回收成功。

验收：`ExitDeadlineReminderTest` 覆盖阈值、不改派；`ExitAssignmentNotificationTest` 覆盖独立模板与收件人；`ConfigurationServiceTest` 覆盖配置边界与无效用户；`B0InfrastructureIT` 在隔离 MySQL 中验证 V47 迁移和双收件箱。`./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test verify` 重跑通过（`B0InfrastructureIT` 15 项、0 失败/错误）；`corepack pnpm --filter @ism/admin build` 通过，当前 Node 20.12.0 低于 Vite 提示的最低版本，需升级本机 Node。首次完整集成测试因本次新增测试用户清理遗漏触发外键错误，已修复并重跑通过。

限制：目前是一层、租户级升级负责人，不是组织逐级上报链；没有跨业务统一任务中心或外部渠道。配置接收人后若被停用，需管理员修正配置或恢复账号，失败台账可见。
