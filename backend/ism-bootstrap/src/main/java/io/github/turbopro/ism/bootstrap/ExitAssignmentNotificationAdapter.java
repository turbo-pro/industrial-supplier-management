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
    @Override public void reminded(long supplierId,long applicationId,long entityId,String code,long sourceId,long assigneeId,java.time.LocalDate dueDate){
        messages.notifyExitReminder(supplierId,applicationId,entityId,code,sourceId,assigneeId,dueDate);
    }
    @Override public void escalated(long supplierId,long applicationId,long entityId,String code,long sourceId,long assigneeId,long recipientId,java.time.LocalDate dueDate){
        messages.notifyExitEscalation(supplierId,applicationId,entityId,code,sourceId,assigneeId,recipientId,dueDate);
    }
    @Override public void accessAssigned(long supplierId,long applicationId,long taskId,String channel,long assigneeId,java.time.LocalDate dueDate){
        messages.notifyExitAccessAssignment(supplierId,applicationId,taskId,channel,assigneeId,dueDate);
    }
}
