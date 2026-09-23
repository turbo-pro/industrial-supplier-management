package io.github.turbopro.ism.resource.print;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.turbopro.ism.common.infrastructure.tenant.TenantContext;
import io.github.turbopro.ism.operation.AsyncTaskService;
import io.github.turbopro.ism.operation.OperationModels;
import io.github.turbopro.ism.resource.file.FileService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PrintTaskExecutor {
    private static final Pattern VARIABLE=Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_.-]{0,63})}}");
    private final PrintJobDispatchMapper mapper;private final PdfRenderer renderer;private final FileService files;private final AsyncTaskService tasks;private final ObjectMapper json;
    public PrintTaskExecutor(PrintJobDispatchMapper mapper,PdfRenderer renderer,FileService files,AsyncTaskService tasks,ObjectMapper json){this.mapper=mapper;this.renderer=renderer;this.files=files;this.tasks=tasks;this.json=json;}
    public void execute(OperationModels.AsyncTask task,String nodeId){
        try(TenantContext.Scope ignored=TenantContext.open(task.tenantId(),task.requesterId())){
            tasks.progress(task.id(),nodeId,10,"读取打印模板");var job=mapper.job(task.tenantId(),task.id());if(job==null)throw new IllegalStateException("打印任务明细不存在");
            Map<String,String> variables=readMap(job.renderPayload());Map<String,String> page=readMap(job.pageConfig());String rendered=replace(job.htmlContent(),variables);
            tasks.progress(task.id(),nodeId,45,"渲染 PDF");byte[] pdf=renderer.render(rendered,page.get("pageSize"),page.get("orientation"),job.templateName());
            tasks.progress(task.id(),nodeId,80,"保存打印文件");var file=files.createGenerated(safeFileName(job.templateName())+"-"+task.taskNo()+".pdf","application/pdf",pdf);
            if(!tasks.attachResult(task.id(),nodeId,Long.parseLong(file.id()))||!tasks.complete(task.id(),nodeId))throw new IllegalStateException("打印任务租约已失效");
        }catch(Exception e){tasks.fail(task.id(),nodeId,"PRINT_RENDER_FAILED",rootMessage(e));}
    }
    private Map<String,String> readMap(String value){try{return json.readValue(value,new TypeReference<Map<String,String>>(){});}catch(Exception e){throw new IllegalStateException("打印参数无效",e);}}
    private String replace(String html,Map<String,String> values){Matcher matcher=VARIABLE.matcher(html);StringBuffer result=new StringBuffer();while(matcher.find())matcher.appendReplacement(result,Matcher.quoteReplacement(escape(values.get(matcher.group(1)))));matcher.appendTail(result);return result.toString();}
    private String escape(String value){if(value==null)return "";return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private String safeFileName(String value){String result=value.replaceAll("[\\\\/:*?\"<>|\\r\\n]","_").trim();return result.isBlank()?"打印文件":result;}
    private String rootMessage(Throwable error){Throwable root=error;while(root.getCause()!=null)root=root.getCause();String message=root.getMessage();return message==null?root.getClass().getSimpleName():message;}
}
