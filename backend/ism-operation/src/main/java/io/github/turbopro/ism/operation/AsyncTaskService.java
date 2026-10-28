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

    /** Each claim has a unique leaseOwner; all writes must use the returned value, never the node name. */
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
        String owner = DispatchLease.owner(nodeId, leaseDuration);
        LocalDateTime now = utcNow();
        OperationModels.AsyncTask task = taskType==null?dispatchMapper.lockNextTask(now):dispatchMapper.lockNextTaskOfType(taskType,now);
        if (task == null) {
            return Optional.empty();
        }
        return dispatchMapper.claimTask(task.id(), owner, now, now.plus(leaseDuration)) == 1
                ? Optional.of(dispatchMapper.claimedTask(task.id())) : Optional.empty();
    }

    @Transactional
    public boolean renew(long taskId, String nodeId, Duration leaseDuration) {
        DispatchLease.validateDuration(leaseDuration);
        LocalDateTime now = utcNow();
        return checkpoint(taskId, nodeId, now, dispatchMapper.renewTask(taskId, nodeId, now, now.plus(leaseDuration)));
    }

    @Transactional
    public boolean complete(long taskId, String nodeId) {
        LocalDateTime now = utcNow();
        return checkpoint(taskId, nodeId, now, dispatchMapper.completeTask(taskId, nodeId, now));
    }

    @Transactional
    public boolean progress(long taskId,String nodeId,int progress,String stage){
        if(progress<0||progress>99)throw new IllegalArgumentException("运行中进度必须在 0 到 99 之间");
        LocalDateTime now = utcNow();
        return checkpoint(taskId,nodeId,now,dispatchMapper.updateTaskProgress(taskId,nodeId,progress,stage,now));
    }

    @Transactional
    public boolean attachResult(long taskId,String nodeId,long fileId){
        LocalDateTime now = utcNow();
        return checkpoint(taskId,nodeId,now,dispatchMapper.attachTaskResult(taskId,nodeId,fileId,now));
    }

    @Transactional
    public boolean fail(long taskId,String nodeId,String errorCode,String message){
        String safe=message==null?null:message.substring(0,Math.min(message.length(),500));
        LocalDateTime now = utcNow();
        return checkpoint(taskId,nodeId,now,dispatchMapper.failTask(taskId,nodeId,errorCode,safe,now));
    }

    private boolean checkpoint(long taskId, String owner, LocalDateTime now, int written) {
        if (written == 1) return true;
        // A cancellation request ends at the next safe worker checkpoint, under the same lease.
        dispatchMapper.settleTaskCancellation(taskId, owner, now);
        return false;
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
