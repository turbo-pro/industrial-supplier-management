# 可配置审批工作流

## 能力边界

该模块提供租户内的通用人工审批闭环：流程草稿、校验、版本发布、业务发起、候选人认领、审批或驳回、实例归档。业务模块只保存自己的业务数据，通过 `businessType + businessId` 与流程实例关联。

当前版本采用“受限 BPMN”，支持开始/结束、人工任务、顺序流及常用网关；不开放脚本任务、服务任务、调用活动、监听器或节点属性表达式。这样可避免租户上传的流程定义执行代码、访问网络或读取服务器数据。业务自动化动作后续通过平台审核过的动作注册表扩展，不允许在 BPMN 中直接写类名或 URL。

## 版本规则

- 流程编码是租户内稳定业务标识，创建后不可修改。
- 编辑只修改草稿；每次发布生成不可变版本，并记录 Flowable 部署和流程定义 ID。
- 新实例固定使用发布时的精确流程定义 ID，后续发布不会改变运行中的实例。
- 更新和发布均使用 `version` 乐观锁，冲突返回 409。

## 租户与权限

- 自研元数据表始终按 `tenant_id` 查询，Flowable 部署、实例和任务同时写入 tenantId。
- 待办列表只返回当前用户可认领或已认领的活动任务。
- 未认领任务只能由 Flowable 候选用户认领；办理只能由当前受理人完成。
- 流程变量限制为最多 50 个标量值，系统保留变量不可覆盖，接口不返回完整变量，避免敏感数据泄露。

权限点：`workflow:definition:view`、`workflow:definition:manage`、`workflow:instance:start`、`workflow:task:view`、`workflow:task:operate`。

## BPMN 示例

流程的 `process id` 必须与流程编码相同，且 `isExecutable=true`。候选人目前使用用户 ID：

```xml
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn" targetNamespace="ism">
  <process id="supplier_approval" isExecutable="true">
    <startEvent id="start"/>
    <userTask id="review" name="供应商审核" flowable:candidateUsers="1001,1002"/>
    <endEvent id="end"/>
    <sequenceFlow id="f1" sourceRef="start" targetRef="review"/>
    <sequenceFlow id="f2" sourceRef="review" targetRef="end"/>
  </process>
</definitions>
```

## 验收清单

1. 不同租户可创建相同流程编码，且互不可见。
2. 非法 XML、process id 不一致、非可执行流程、多个 process、脚本/服务任务、监听器、外部实体和属性表达式均拒绝发布。
3. 发布产生新版本；旧实例仍绑定旧版本，新实例使用最新版本。
4. 非候选用户无法认领，用户无法办理他人任务，跨租户任务 ID 返回不存在。
5. 审批意见写入 Flowable comment；流程结束后业务实例状态变为 `COMPLETED`。
6. 并发编辑或发布只有一个请求成功，另一个返回版本冲突。
