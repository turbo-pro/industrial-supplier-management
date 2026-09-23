package io.github.turbopro.ism.resource.print;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PrintJobDispatchMapper {
    @Select("""
        SELECT j.id,j.task_id,j.template_version_id,t.template_name,v.html_content,v.page_config,j.render_payload
        FROM prt_print_job j
        JOIN prt_template_version v ON v.id=j.template_version_id AND v.tenant_id=j.tenant_id
        JOIN prt_template t ON t.id=v.template_id AND t.tenant_id=j.tenant_id
        WHERE j.tenant_id=#{tenantId} AND j.task_id=#{taskId}
        """)
    PrintModels.JobRow job(long tenantId,long taskId);
}
