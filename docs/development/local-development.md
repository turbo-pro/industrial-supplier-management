# 本地开发与 B0 验收

## 1. 环境要求

- JDK 17 或更高版本
- Docker Desktop 或兼容的 Docker Engine
- Git

项目使用 Maven Wrapper，执行 `./mvnw` 即可，不要求全局安装 Maven。B0 阶段的默认基础设施只有 MySQL 8.0。

## 2. 启动 MySQL

```bash
cp deploy/docker/.env.example deploy/docker/.env
docker compose --env-file deploy/docker/.env -f deploy/docker/compose.dev.yml up -d --wait
```

`.env` 只用于本机且不会提交。示例值用于本地开发，不可直接用于 Demo 或生产环境。

## 3. 启动后端

```bash
./mvnw -pl backend/ism-bootstrap -am spring-boot:run
```

默认连接参数如下：

| 配置 | 默认值 | 环境变量 |
| --- | --- | --- |
| 数据库地址 | `jdbc:mysql://localhost:3306/industrial_supplier...` | `ISM_DB_URL` |
| 数据库用户 | `ism` | `ISM_DB_USERNAME` |
| 数据库密码 | `ism` | `ISM_DB_PASSWORD` |
| HTTP 端口 | `8080` | `ISM_SERVER_PORT` |

应用启动时 Flyway 会执行版本化迁移，Flowable 会维护自己的引擎表。健康检查地址为：

```text
GET http://localhost:8080/actuator/health
```

验收结果应为 HTTP 200，响应状态为 `UP`。

## 4. 验证命令

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

## 5. 停止环境

```bash
docker compose --env-file deploy/docker/.env -f deploy/docker/compose.dev.yml down
```

该命令保留 MySQL 数据卷。只有在明确不再需要本地数据时才使用 `down --volumes`。
