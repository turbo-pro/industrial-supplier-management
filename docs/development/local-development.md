# 本地开发与 B0 验收

## 1. 环境要求

- JDK 17 或更高版本
- Docker Desktop 或兼容的 Docker Engine
- Git

项目使用 Maven Wrapper，执行 `./mvnw` 即可，不要求全局安装 Maven。B0 阶段的默认基础设施只有 MySQL 8.0。

## 2. 启动 MySQL

推荐直接使用一键启动命令，它会依次启动 MySQL、后端、管理后台和 Console，并等待后端健康检查通过：

```bash
./scripts/dev.sh up
```

常用管理命令：

```bash
./scripts/dev.sh status   # 查看状态
./scripts/dev.sh logs     # 汇总查看三个应用日志
./scripts/dev.sh restart  # 重启整套本地环境
./scripts/dev.sh down     # 停止应用和 MySQL，保留数据库数据卷
```

以下章节保留分步启动方式，便于排查问题。

```bash
cp deploy/docker/.env.example deploy/docker/.env
docker compose --env-file deploy/docker/.env -f deploy/docker/compose.dev.yml up -d --wait
```

`.env` 只用于本机且不会提交。示例值用于本地开发，不可直接用于 Demo 或生产环境。

## 3. 启动后端

```bash
./mvnw -pl backend/ism-bootstrap -am package -DskipTests -DskipITs
java -jar backend/ism-bootstrap/target/ism-bootstrap-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

默认连接参数如下：

| 配置 | 默认值 | 环境变量 |
| --- | --- | --- |
| 数据库地址 | `jdbc:mysql://localhost:3306/industrial_supplier...` | `ISM_DB_URL` |
| 数据库用户 | `ism` | `ISM_DB_USERNAME` |
| 数据库密码 | `ism` | `ISM_DB_PASSWORD` |
| HTTP 端口 | 默认环境 `8080`，local Profile 固定为 `18080` | `ISM_SERVER_PORT`（非 local） |
| 中文打印字体 | 无，使用打印功能前必须配置标准 TrueType 字体 | `ISM_PRINT_FONT_PATH` |

例如 Linux 可安装 Noto Sans CJK 并将字体集合中的简体中文字体导出为独立 TTF 后配置；不要直接传入 `.ttc` 字体集合。若暂不验证打印任务，可设置 `ISM_PRINT_WORKER_ENABLED=false` 停止打印 Worker。

应用启动时 Flyway 会执行版本化迁移，Flowable 会维护自己的引擎表。健康检查地址为：

```text
GET http://localhost:18080/actuator/health
```

验收结果应为 HTTP 200，响应状态为 `UP`。

`local` Profile 还会加载仅用于本机的演示数据：

| 入口 | 账号 | 密码 |
| --- | --- | --- |
| Console `http://localhost:4174` | `platform-admin` | `Admin@123456` |
| 管理后台 `http://localhost:4173` | 租户 `demo`、用户 `admin` | `Admin@123456` |

本地数据脚本不在默认 Flyway 路径中，未启用 `local` Profile 时不会创建测试账号。

## 4. 启动前端

环境要求 Node.js 20.19+ 和 pnpm 10.17.1。首次运行：

```bash
corepack enable
corepack prepare pnpm@10.17.1 --activate
pnpm install
```

分别在两个终端启动租户管理后台和平台 Console：

```bash
pnpm --filter @ism/admin dev
pnpm --filter @ism/console dev
```

Vite 会把 `/api` 转发到 `http://127.0.0.1:18080`，浏览器不需要额外配置跨域。local Profile 使用独立端口，避免与机器上常见的 8080 服务冲突。

## 5. 验证命令

普通构建不依赖 Docker：

```bash
./mvnw verify
```

基础设施集成测试会通过 Testcontainers 创建一次性 MySQL，自动验证以下闭环：

1. Spring Boot 上下文可启动；
2. Flyway 迁移成功；
3. MyBatis 可写入、查询并删除数据；
4. Flowable 可部署、启动并完成人工任务流程。

```bash
./mvnw -pl backend/ism-bootstrap -am -Pintegration-test verify
```

若 Docker Engine 不可用，集成测试会被标记为跳过；CI 环境必须执行且通过该测试。

## 6. 停止环境

```bash
docker compose --env-file deploy/docker/.env -f deploy/docker/compose.dev.yml down
```

该命令保留 MySQL 数据卷。只有在明确不再需要本地数据时才使用 `down --volumes`。
