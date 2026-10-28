# B2-06 任务取消和重试闭环

日期：2026-10-28。来源：PRD 20 的 OPS 状态与安全检查点规则，仓内 `task-center.md` 的取消及三次重试约定。前置为 B0-07 的独立领取标识和租约保护；状态为此子任务已验收，不标记整个 B2-06 完成。

## 任务卡

修改范围限 `ism-operation` 任务服务/Mapper、隔离数据库测试、打印执行器测试和任务 API 文档/生成客户端。没有新迁移或页面变更，不处理旧数据库历史修复。

| 验收编号 | 独立目标 | 实际入口 |
|---|---|---|
| TASK-CANCEL-AT-001 | 排队取消即时结束且 finishedAt 非空，不能再领取 | POST /api/tasks/{id}/cancel |
| TASK-CANCEL-AT-002 | 运行取消登记请求，当前有效执行在下一个安全检查点终结为 CANCELLED，清除 lease 和结果引用 | progress / renew / attachResult / fail / complete |
| TASK-CANCEL-AT-003 | 取消与完成真实双连接争用，最终只能取消或成功；旧租约不能代新租约终结取消 | DispatchLeaseIT |
| TASK-RETRY-AT-001 | FAILED/CANCELLED 按版本重试，实际重试最多三次，重复提交不重复计数 | POST /api/tasks/{id}/retry |
| TASK-RETRY-AT-002 | 重试清除旧文件引用、时间、取消标记和错误；旧执行不能影响新执行 | 任务服务 + 租约执行器 |
| TASK-ACCESS-AT-001 | 当前租户本人可读/操作；其他账号/租户拒绝，操作权限实时收紧，未登录拒绝 | 真实 HTTP 与服务层隔离验收 |
| TASK-PRINT-AT-001 | 打印阶段检查失败后不再执行下一阶段、保存文件或确认成功 | PrintTaskExecutorTest 三个阶段 |

## 状态与并发

取消仍为协作停止，不强制中断正在渲染的线程。工作节点每个写检查点要求 cancel_requested=0；若写入拒绝，使用当前独立 leaseOwner 尝试终结取消。终结同样要求未过期租约，旧节点不能代新执行取消。取消请求与完成由数据库当前读及更新行锁串行化；完成先获胜则取消版本冲突，取消先获胜则执行不能写成成功。

排队取消先按原状态计算结束时间，再更新状态，修复 MySQL 从左到右赋值导致 finished_at 丢失。重试次数在成功重排时递增；旧实现只在失败时加计数，导致取消可无限重试而失败少一次重试。历史旧计数原样保留，仍受三次上限约束，不自动重置或回填。

失败、取消终结和重新排队均不再把上一轮生成结果作为当前可用任务结果。仅清理任务引用；文件对象的独立保留/回收策略不在此子任务，不假装已经删除实际文件。任务中心 API 不新增响应字段，补充 404 与取消/重试状态说明，重新生成类型客户端。

## 证据

最终隔离 MySQL 12/12 通过，含真实 HTTP 登录、强制改密、取消、检查点终结、读取、重试、旧请求 409、旧执行拒绝及权限撤回 403。既有任务/Outbox/真实打印 PDF 回归 2/2，打印检查点 3/3、客户端 10/10、客户端类型构建通过，无失败、错误或跳过。

证据目录 `output/playwright/task-delivery-2026-10-28/`：`task-cancellation-final-tests.txt`、`task-cancellation-final-regression.txt`、`task-cancellation-print-checkpoints.txt`、`task-cancellation-client.log`、`task-cancellation-client-build.log`。复现命令：JDK 21 下运行 `./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test '-Dit.test=DispatchLeaseIT,B0InfrastructureIT#shouldAuditSanitizeRetryIdempotentlyAndRecoverExpiredLeases+shouldProvisionTenantHeadquartersAndFirstAdministrator' -Dfailsafe.failIfNoSpecifiedTests=false verify`。

Review：无待修复的本子任务阻断；测试容器和临时存储已清理，现有开发 MySQL 未修改。接口注释与生成类型同步，提交前生成一致性检查通过。数据库统一时间及真实锁等待补验追加后，最新专项为15/15，既有业务回归2/2、打印检查点3/3仍通过，详见 [后续租约 Review](dispatch-lease-fencing-review.md)。

本轮没有改变旧库、修复迁移校验和或清库。升级/回滚沿用 [租约部署约定](dispatch-lease-fencing-review.md)：协调停止工作节点，禁止混用旧调度实现。回滚代码将恢复原取消/重试缺陷，应在无在途任务时执行。

## 剩余

未覆盖所有任务类型的业务副作用幂等、自动重试调度、孤立文件回收、独立执行尝试历史/审计、任务 TTL、容量与进程宕机压测。对外部系统投递不宣称 exactly-once。B2-05 人工业务待办与此异步任务中心是不同任务包。
