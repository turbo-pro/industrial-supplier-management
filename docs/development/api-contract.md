# 统一 API 契约

本契约适用于 `/api/v1` 下的管理后台、Console、供应商门户和移动 H5 接口。业务模块不得自行创建另一套响应、分页或异常结构。

## 成功响应

```json
{
  "success": true,
  "data": {},
  "traceId": "9f53b1c28e2d4fb8b540356245410f5d",
  "timestamp": "2026-09-19T21:00:00.000+08:00"
}
```

Controller 使用 `ApiResponseFactory.success(data)` 创建响应。数据库 Entity 不得直接作为 `data` 返回。

分页数据统一使用 `PageResponse<T>`：

```json
{
  "items": [],
  "page": 1,
  "pageSize": 20,
  "total": 0
}
```

页码从 1 开始。各模块不得改用 `rows/list/records/current/size` 等其他字段名。

## 失败响应

```json
{
  "success": false,
  "error": {
    "code": "COMMON_VALIDATION_FAILED",
    "message": "请求参数校验失败",
    "fieldErrors": [],
    "details": {},
    "retryable": false
  },
  "traceId": "9f53b1c28e2d4fb8b540356245410f5d",
  "timestamp": "2026-09-19T21:00:00.000+08:00"
}
```

可预期业务失败抛出 `ApiException`，并传入实现 `ErrorCode` 的模块错误码。未知异常统一映射为 `COMMON_INTERNAL_ERROR`，响应不得包含异常类、堆栈、SQL、连接串、内部路径、口令或密钥。

模块错误码使用大写下划线，格式为 `{MODULE}_{REASON}`，并遵循以下 HTTP 语义：

| 场景 | HTTP | 错误码后缀或前缀 |
| --- | ---: | --- |
| 参数校验 | 400 | `COMMON_VALIDATION_*` |
| 未认证 | 401 | `IAM_UNAUTHENTICATED_*` |
| 明确允许披露的越权 | 403 | `IAM_FORBIDDEN_*` |
| 不存在或隐藏越权对象 | 404 | `*_NOT_FOUND` |
| 状态、版本或重复冲突 | 409 | `*_STATE_CONFLICT`、`*_VERSION_CONFLICT`、`*_DUPLICATE` |
| 业务规则阻断 | 422 | `*_RULE_BLOCKED` |
| 限流 | 429 | `COMMON_RATE_LIMITED` |
| 外部依赖不可用 | 503 | `*_EXTERNAL_UNAVAILABLE` |

`message` 面向用户且后续可国际化。`details` 只能放可安全公开、可帮助前端呈现或处理的数据，禁止放调试信息。

## 字段校验

请求 DTO 使用 Jakarta Validation。校验失败时，`fieldErrors` 每项固定包含：

- `field`：请求字段路径；
- `code`：稳定的校验类型；
- `message`：用户可读提示。

同一响应中的字段错误按字段名排序，便于自动化测试稳定断言。

## TraceId

- 请求可通过 `X-Trace-Id` 携带 8–64 位字母、数字、下划线或短横线组成的追踪号；
- 不符合规则的请求值不会进入日志，服务端重新生成 32 位小写十六进制追踪号；
- 响应头、响应体和同一请求的日志 MDC 使用同一个 traceId；
- 客户端报错时应记录 traceId，但不能用 traceId 替代审计事件或业务幂等键。

## 验收要求

每个新增接口至少验证：成功结构、参数错误、声明的业务错误码、HTTP 状态、traceId，以及未知异常不泄露内部信息。认证过滤器产生的 401/403 响应将在 B0-04 使用同一失败结构接入。
