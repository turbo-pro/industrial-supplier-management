# B0-03 HTTP 输入与服务端错误边界

日期：2026-10-29。任务包 B0-03，来源为 PRD 40 的统一 API 契约以及既有必填头/参数约定；关联 B0-08、GAP-13 的错误信息脱敏。范围限通用异常处理、契约测试和真实服务隔离数据库验收，没有新迁移、页面或业务字段调整。

## 问题与验收目标

先加回归再修复：缺失必填请求头及 Spring MVC 原生方法参数校验两项均实际返回500；修复前7项契约测试中2项失败，另5项基线正常。补充以下边界：

| 验收号 | 独立目标 |
|---|---|
| HTTP-INPUT-AT-001 | 缺失必填 Header、Cookie、Multipart Part 返回400和COMMON_VALIDATION_FAILED，提供稳定字段名和Required码，不能产生业务写入 |
| HTTP-INPUT-AT-002 | 原生MVC的查询/路径参数约束返回400，保留请求别名及Min/Max等稳定约束码；合法边界值仍成功 |
| HTTP-INPUT-AT-003 | 缺失查询、参数类型错误、非法JSON、405和415保留既有统一响应及traceId，不回显请求值或框架内部签名 |
| HTTP-SERVER-AT-001 | 返回值校验失败、错误路由定义及未知异常仍为500；不把服务端缺陷标记成客户端校验错误，不暴露返回值、签名或堆栈 |
| HTTP-FILE-AT-001 | 真实登录及强制改密后，上传初始化/分片/完成缺少必要头均400且对应表无写入；补齐后完成上传并下载相同字节，未登录下载401 |

## 实现与行为

新增特定绑定异常与HandlerMethodValidationException处理；输入失败沿用公共400错误码，方法参数名采用RequestParam/PathVariable/RequestHeader的显式别名。原生方法校验不序列化拒绝的参数或框架异常消息，排序稳定。

返回值校验失败（包括ConstraintViolationException中的RETURN_VALUE）及MissingPathVariableException归为服务端错误。后者表示Controller注解引用了路由中不存在的变量，不能靠用户修改请求解决。服务端日志仍只记录异常类型与traceId，响应保持通用内部错误。

已有Bean输入校验、自定义业务错误、状态冲突、不可读请求、认证和权限链保留原约定。新路由仅存在测试控制器；生产API字段和请求格式未新增，因此没有生成客户端或修改他人正在调整的OpenAPI文件。

## 验证与回滚

扩展契约测试13/13通过，包含修复前的两个失败用例及服务端错误分支；真实MySQL随机端口HTTP上传/下载首轮及最终回归均1/1通过，无失败、错误或跳过；最终报告为 `http-input-final-tests.txt` 与 `api-input-final-tests.txt`。隔离容器和临时存储已清理，未修改现有开发库。Review无本子任务阻断。证据目录 `output/playwright/foundation-2026-10-29/`：`api-input-before-fix.log`、`api-input-after-fix.log`、`http-input-integration.log`、`http-input-final.log`。

复现：JDK21下 `./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test -Dit.test=HttpInputBoundaryIT -Dfailsafe.failIfNoSpecifiedTests=false verify`；正常单测也包含ApiContractTest和模块边界门禁。没有跳过数据库场景，不使用Mock接口替代真实上传闭环。

无持久化格式变更，回滚应用即可；旧程序会重新出现将缺失头/方法参数错误映射为500的问题。不得因此repair迁移或清理业务库。未覆盖所有请求体大小限制、全产品业务校验消息审计和全部控制器参数组合，不将本子任务当作整个B0/B1或安全Gate完成。
