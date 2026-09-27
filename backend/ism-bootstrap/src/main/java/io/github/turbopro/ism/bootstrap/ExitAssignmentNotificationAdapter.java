package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.operation.message.MessageService;
import io.github.turbopro.ism.supplier.ExitAssignmentNotifier;
import org.springframework.stereotype.Component;

@Component
public class ExitAssignmentNotificationAdapter implements ExitAssignmentNotifier {
    private final MessageService messages;
    public ExitAssignmentNotificationAdapter(MessageService messages){this.messages=messages;}
    @Override public void assigned(long supplierId,long applicationId,long entityId,String code,long sourceId,long assigneeId){
        messages.notifyExitAssignment(supplierId,applicationId,entityId,code,sourceId,assigneeId);
    }
}
