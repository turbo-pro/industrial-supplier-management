package io.github.turbopro.ism.platform.packageplan;

import io.github.turbopro.ism.common.api.error.*;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.iam.authorization.TenantNotificationRecipients;
import io.github.turbopro.ism.operation.message.MessageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;

@Service
public class SubscriptionReminderService {
    private final SubscriptionReminderMapper mapper;
    private final PackagePlanMapper packages;
    private final TenantNotificationRecipients recipients;
    private final MessageService messages;
    private final TransactionTemplate transactions;

    public SubscriptionReminderService(SubscriptionReminderMapper mapper,PackagePlanMapper packages,
            TenantNotificationRecipients recipients,MessageService messages,PlatformTransactionManager manager) {
        this.mapper=mapper;this.packages=packages;this.recipients=recipients;this.messages=messages;
        transactions=new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public List<SubscriptionReminderModels.Candidate> candidates(long cursor,int size) { return mapper.candidates(cursor,size); }
    public List<SubscriptionReminderModels.Reminder> history(long tenantId,long subscriptionId) {
        requireSubscription(tenantId,subscriptionId); return mapper.history(tenantId,subscriptionId);
    }
    public SubscriptionReminderModels.Dispatch dispatch(long tenantId,long subscriptionId) {
        return dispatch(tenantId,subscriptionId,null);
    }
    public SubscriptionReminderModels.Dispatch dispatchAsOperator(long tenantId,long subscriptionId,long actorId) {
        if(actorId<=0) throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        return dispatch(tenantId,subscriptionId,actorId);
    }
    private SubscriptionReminderModels.Dispatch dispatch(long tenantId,long subscriptionId,Long actorId) {
        requireSubscription(tenantId,subscriptionId);
        String[] stage={null};
        try(var context=actorId==null?TenantContext.openSystem(tenantId):TenantContext.open(tenantId,actorId)) {
            try {
                return transactions.execute(tx->{
                    if(packages.lockTenant(tenantId)==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
                    var due=mapper.due(tenantId);
                    if(due==null || due.subscriptionId()!=subscriptionId) return skipped(null);
                    stage[0]=due.stage(); var now=packages.utcNow();
                    mapper.ensure(tenantId,subscriptionId,due.stage(),now);
                    if("SENT".equals(mapper.reminder(tenantId,subscriptionId,due.stage()).status())) return skipped(due.stage());
                    var users=recipients.activeAdministrators(tenantId);
                    if(users.isEmpty()) throw new ApiException(CommonErrorCode.VALIDATION_FAILED,"无有效租户管理员");
                    var sent=messages.notifySubscriptionExpiry(subscriptionId,users,due.versionName(),due.expiresAt(),due.stage());
                    mapper.sent(tenantId,subscriptionId,due.stage(),sent.delivered(),now);
                    return new SubscriptionReminderModels.Dispatch("SENT",due.stage(),sent.delivered(),null);
                });
            } catch(RuntimeException failure) {
                if(stage[0]==null) throw failure;
                String error=failure instanceof ApiException api?api.errorCode().code():"DELIVERY_FAILED";
                return transactions.execute(tx->{
                    packages.lockTenant(tenantId);
                    mapper.failed(tenantId,subscriptionId,stage[0],error,packages.utcNow());
                    var row=mapper.reminder(tenantId,subscriptionId,stage[0]);
                    return "SENT".equals(row.status())?skipped(stage[0]):new SubscriptionReminderModels.Dispatch("FAILED",stage[0],0,error);
                });
            }
        }
    }
    private void requireSubscription(long tenantId,long subscriptionId) {
        if(packages.subscription(tenantId,subscriptionId)==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
    }
    private SubscriptionReminderModels.Dispatch skipped(String stage) { return new SubscriptionReminderModels.Dispatch("SKIPPED",stage,0,null); }
}
