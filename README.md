# Industrial Supplier Management

面向制造业和化工企业的开源供应商与承包商全生命周期管理平台。

> 项目正在进行工程骨架建设，尚未发布可用于生产的版本。

## 产品范围

- 供应商注册、档案、资格、证照、准入、复审与退出
- 合同、项目、分包、人员、车辆、设备和工器具
- 培训、交底、入场、开工、检查、隐患、停复工和事故
- 质量验收、异常、CAPA、绩效、限制和黑名单
- 多租户、单/多场站、总部视角、Console和供应商门户
- 文件、消息、待办、任务、搜索、打印和受控工作流

## 技术基线

- Java 17、Spring Boot 3.5、MyBatis、MySQL、Flyway、Flowable 7
- Vue 3、TypeScript、Element Plus、Vite、pnpm
- Monorepo、模块化单体、Docker Compose
- Local文件存储默认，预留MinIO/S3；Redis可选；Kafka非默认

## 仓库结构

```text
backend/        后端模块化单体
frontend/       Console、管理后台、供应商门户、移动H5与共享包
contracts/      OpenAPI和跨模块稳定契约
database/       数据库迁移说明
deploy/         本地、Demo和企业部署
docs/           架构、决策与开发文档
tests/          端到端、夹具和性能测试
```

## 当前阶段

当前只建设B0工程与验证骨架。业务模块必须在数据、API、权限和验收契约冻结后实施。

## 许可证

Apache License 2.0。详见 [LICENSE](LICENSE)。

## 参与项目

请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 和 [SECURITY.md](SECURITY.md)。
