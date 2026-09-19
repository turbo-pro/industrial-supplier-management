# Docker deployment

本目录提供本地开发所需的最小基础设施。B0 阶段只默认启动 MySQL；Redis、Kafka、对象存储等组件在出现明确业务需求后再按 profile 引入。

```bash
cp deploy/docker/.env.example deploy/docker/.env
docker compose --env-file deploy/docker/.env -f deploy/docker/compose.dev.yml up -d --wait
```

停止容器但保留数据库数据：

```bash
docker compose --env-file deploy/docker/.env -f deploy/docker/compose.dev.yml down
```

如需同时删除本地开发数据，可显式追加 `--volumes`。
