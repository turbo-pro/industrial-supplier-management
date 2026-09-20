# 认证与令牌契约

## 接口

| 接口 | 认证 | 说明 |
| --- | --- | --- |
| `POST /api/auth/login` | 否 | 租户代码、用户名、密码和设备标识登录 |
| `POST /api/auth/refresh` | 否 | 轮换访问令牌和刷新令牌 |
| `POST /api/auth/logout` | 是 | 撤销当前令牌族 |
| `GET /api/auth/me` | 是 | 获取当前用户摘要 |
| `POST /api/auth/change-password` | 是 | 校验旧密码并修改密码 |

## 令牌规则

- 访问令牌为 HS256 JWT，默认有效期 15 分钟；生产和 Demo 必须通过 `ISM_JWT_SECRET` 设置不少于 32 字节的随机密钥。
- 刷新令牌为 256 位不透明随机值，默认有效期 7 天；数据库只保存 SHA-256 哈希。
- 每次刷新都轮换刷新令牌。已轮换令牌再次出现视为重放，并撤销同一令牌族。
- 访问 JWT 携带 `tokenVersion` 和 `tokenFamilyId`；每次请求校验用户状态、令牌版本及令牌族状态。
- 退出、密码修改、账号禁用或重放检测后，相应访问令牌不能继续访问业务接口。
- 应用节点不保存本地 Session，多个节点共享 MySQL 中的撤销状态。

## 密码规则

默认 BCrypt 强度为 12。新密码至少 12 位，并同时包含大写字母、小写字母、数字和特殊字符，且不能与当前密码相同。

连续登录失败默认达到 5 次后锁定 15 分钟。不存在账号和密码错误对外统一返回 `IAM_INVALID_CREDENTIALS`，避免账号枚举。

首次登录或密码过期用户获得带 `passwordChangeRequired` 标记的令牌，只允许调用修改密码和退出接口。改密成功后递增 `tokenVersion` 并撤销该用户所有刷新令牌。

## 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `ISM_JWT_SECRET` | 仅本地开发值 | JWT 签名密钥 |
| `ISM_JWT_ACCESS_TTL` | `PT15M` | 访问令牌有效期 |
| `ISM_JWT_REFRESH_TTL` | `P7D` | 刷新令牌有效期 |
| `ISM_LOGIN_MAX_FAILURES` | `5` | 锁定前连续失败次数 |
| `ISM_LOGIN_LOCK_DURATION` | `PT15M` | 临时锁定时间 |

日志、审计和接口响应均不得记录密码、访问令牌或刷新令牌。
