# B1-07 项目权限浏览器回归修复

2026-11-06。GitHub Actions run 42052608825 的 project-entry 驱动在撤权后报 “Expected two preserved projects”。只读权限提示不依赖异步列表数据；测试在提示出现后立即读取按钮数，未等两条项目记录加载。修复仅限撤权浏览器断言：等待总部历史项目和场站项目两条指定记录出现，再检查恰好两个详情入口且均禁用。保留真实详情 HTTP 403、旧写入口拒绝、最终数据库记录不变及无项目写审计断言。无需迁移，回滚仅撤回测试改动。

同一远端运行的其余四项失败单列：portal-exit 等待旧“关闭”按钮；portal-recovery 找到按钮但无法滚动到可点击位置；portal-changes 未找到预期审核入口；portal-submissions 超时。未跳过或放宽任何门户断言，未将此次项目专项视为全量门禁通过。

干净暂存副本运行 `node scripts/verify-reminder-e2e.mjs --project-entry`，真实 Chrome、Spring Boot 与隔离 MySQL 通过（1/1）；最终库断言通过。证据：`output/playwright/project-entry-recheck/run.log`。旧开发库未使用。
