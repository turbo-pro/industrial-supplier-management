package io.github.turbopro.ism.operation;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class AsyncTaskService {
    private final TenantOperationMapper tenantMapper;
    private final DispatchOperationMapper dispatchMapper;
    private final OperationIdGenerator ids;
    private final SensitivePayloadSanitizer sanitizer;

    public AsyncTaskService(TenantOperationMapper tenantMapper, DispatchOperationMapper dispatchMapper,
                            OperationIdGenerator ids, SensitivePayloadSanitizer sanitizer) {
        this.tenantMapper = tenantMapper;
        this.dispatchMapper = dispatchMapper;
        this.ids = ids;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public long submit(String taskType, Object payload, int priority) {
        TenantContext.Identity identity = TenantContext.require();
        long id = ids.nextId();
        String taskNo = "TASK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        tenantMapper.insertTask(id, identity.tenantId(), taskNo, taskType, identity.actorId(),
                sanitizer.sanitize(payload), priority);
        return id;
    }

    @Transactional
    public Optional<OperationModels.AsyncTask> claimNext(String nodeId, Duration leaseDuration) {
        return claim(null,nodeId,leaseDuration);
    }

    @Transactional
    public Optional<OperationModels.AsyncTask> claimNext(String taskType,String nodeId,Duration leaseDuration){
        if(taskType==null||taskType.isBlank())throw new IllegalArgumentException("taskType is required");
        return claim(taskType,nodeId,leaseDuration);
    }

    private Optional<OperationModels.AsyncTask> claim(String taskType,String nodeId,Duration leaseDuration){
        LocalDateTime now = utcNow();
        OperationModels.AsyncTask task = taskType==null?dispatchMapper.lockNextTask(now):dispatchMapper.lockNextTaskOfType(taskType,now);
        if (task == null) {
            return Optional.empty();
        }
        return dispatchMapper.claimTask(task.id(), nodeId, now, now.plus(leaseDuration)) == 1
                ? Optional.of(task) : Optional.empty();
    }

    @Transactional
    public boolean renew(long taskId, String nodeId, Duration leaseDuration) {
        LocalDateTime now = utcNow();
        return dispatchMapper.renewTask(taskId, nodeId, now, now.plus(leaseDuration)) == 1;
    }

    @Transactional
    public boolean complete(long taskId, String nodeId) {
        return dispatchMapper.completeTask(taskId, nodeId, utcNow()) == 1;
    }

    @Transactional
    public boolean progress(long taskId,String nodeId,int progress,String stage){
        if(progress<0||progress>99)throw new IllegalArgumentException("运行中进度必须在 0 到 99 之间");
        return dispatchMapper.updateTaskProgress(taskId,nodeId,progress,stage)==1;
    }

    @Transactional
    public boolean attachResult(long taskId,String nodeId,long fileId){return dispatchMapper.attachTaskResult(taskId,nodeId,fileId)==1;}

    @Transactional
    public boolean fail(long taskId,String nodeId,String errorCode,String message){
        String safe=message==null?null:message.substring(0,Math.min(message.length(),500));
        return dispatchMapper.failTask(taskId,nodeId,errorCode,safe,utcNow())==1;
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
