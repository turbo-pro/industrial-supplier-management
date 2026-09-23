package io.github.turbopro.ism.resource.print;

import io.github.turbopro.ism.operation.AsyncTaskService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PrintTaskWorker {
    private final AsyncTaskService tasks;private final PrintTaskExecutor executor;private final PrintProperties properties;
    public PrintTaskWorker(AsyncTaskService tasks,PrintTaskExecutor executor,PrintProperties properties){this.tasks=tasks;this.executor=executor;this.properties=properties;}
    @Scheduled(fixedDelayString="${ism.print.poll-interval:2000}")
    public void poll(){if(!properties.isWorkerEnabled())return;tasks.claimNext("PRINT_DOCUMENT",properties.getNodeId(),properties.getLeaseDuration()).ifPresent(task->executor.execute(task,properties.getNodeId()));}
}
