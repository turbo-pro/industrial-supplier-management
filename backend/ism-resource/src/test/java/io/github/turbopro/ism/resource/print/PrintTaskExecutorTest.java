package io.github.turbopro.ism.resource.print;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.operation.AsyncTaskService;
import io.github.turbopro.ism.operation.OperationModels;
import io.github.turbopro.ism.resource.file.FileService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PrintTaskExecutorTest {
    @ParameterizedTest @ValueSource(ints={10,45,80})
    void stopsAtRejectedLeaseOrCancellationCheckpoint(int rejectedStage) {
        var mapper = mock(PrintJobDispatchMapper.class);
        var renderer = mock(PdfRenderer.class);
        var files = mock(FileService.class);
        var tasks = mock(AsyncTaskService.class);
        String owner = "node:unique-attempt";
        var task = new OperationModels.AsyncTask(10, 20, "TASK-10", "PRINT_DOCUMENT", 30,
                "{}", "RUNNING", 0, owner, LocalDateTime.now().plusMinutes(1), 0, 1,
                null, false, null, null, null, LocalDateTime.now(), LocalDateTime.now(), null);
        when(tasks.progress(eq(10L), eq(owner), anyInt(), anyString())).thenReturn(true);
        when(tasks.progress(eq(10L), eq(owner), eq(rejectedStage), anyString())).thenReturn(false);
        when(mapper.job(20,10)).thenReturn(new PrintModels.JobRow(1,10,2,"模板",
                "<html><body>test</body></html>","{\"pageSize\":\"A4\",\"orientation\":\"PORTRAIT\"}","{}"));
        when(renderer.render(anyString(), anyString(), anyString(), anyString())).thenReturn(new byte[]{1});
        new PrintTaskExecutor(mapper,renderer,files,tasks,new ObjectMapper()).execute(task);
        verify(tasks, never()).complete(anyLong(), anyString());
        verify(tasks, never()).attachResult(anyLong(), anyString(), anyLong());
        verify(tasks, never()).fail(anyLong(), anyString(), anyString(), anyString());
        verifyNoInteractions(files);
        if (rejectedStage == 10) verifyNoInteractions(mapper, renderer);
        if (rejectedStage == 45) verifyNoInteractions(renderer);
        if (rejectedStage == 80) verify(renderer).render(anyString(), anyString(), anyString(), anyString());
    }
}
